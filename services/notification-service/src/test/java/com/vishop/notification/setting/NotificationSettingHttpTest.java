package com.vishop.notification.setting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.command.CreateChannelCommandHandler;
import com.vishop.notification.modules.setting.application.command.UpdateChannelCommandHandler;
import com.vishop.notification.modules.setting.application.query.GetChannelQueryHandler;
import com.vishop.notification.modules.setting.application.query.ListChannelsQueryHandler;
import com.vishop.notification.modules.setting.application.service.NotificationSettingService;
import com.vishop.notification.modules.setting.presentation.rest.*;
import com.vishop.notification.modules.setting.presentation.rest.dto.NotificationSettingUpdateReq;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.security.config.PermissionSecurityAutoConfiguration;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.github.coreplatform.common.web.exception.SecurityExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationSettingHttpTest {
  final NotificationSettingService api = mock(NotificationSettingService.class);
  final UUID tenant = UUID.randomUUID();
  final UUID user = UUID.randomUUID();
  final Map<NotificationChannel, UUID> channelIds =
      Arrays.stream(NotificationChannel.values())
          .collect(java.util.stream.Collectors.toMap(c -> c, c -> UUID.randomUUID()));

  String path(NotificationChannel channel) {
    return "/api/v1/notification-settings/channels/" + channelIds.get(channel);
  }

  MockMvc mvc;
  jakarta.validation.ValidatorFactory validators;
  AnnotationConfigApplicationContext context;

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}

  @BeforeEach
  void setup() {
    validators = jakarta.validation.Validation.buildDefaultValidatorFactory();
    context = new AnnotationConfigApplicationContext();
    context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
    context.registerBean(jakarta.validation.Validator.class, validators::getValidator);
    context.register(MethodSecurityConfiguration.class);
    context.register(PermissionSecurityAutoConfiguration.class);
    context.registerBean(NotificationSettingService.class, () -> api);
    context.register(
        CqrsAutoConfiguration.class,
        UpdateChannelCommandHandler.class,
        CreateChannelCommandHandler.class,
        GetChannelQueryHandler.class,
        ListChannelsQueryHandler.class);
    context.registerBean(NotificationSettingController.class);
    context.refresh();
    when(api.channelById(eq(tenant), any(UUID.class)))
        .thenAnswer(
            invocation -> {
              UUID id = invocation.getArgument(1);
              var channel =
                  channelIds.entrySet().stream()
                      .filter(e -> e.getValue().equals(id))
                      .map(Map.Entry::getKey)
                      .findFirst()
                      .orElseThrow();
              return new NotificationSettingManagementApi.ChannelView(
                  id,
                  channel.name(),
                  channel,
                  true,
                  false,
                  false,
                  false,
                  false,
                  null,
                  null,
                  null,
                  0,
                  Map.of());
            });
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(NotificationSettingController.class))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new SecurityExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    if (context != null) context.close();
    if (validators != null) validators.close();
  }

  void actor(UUID tenantId, Set<String> permissions) {
    var principal = new GatewayPrincipal(user, tenantId, "USER", "MEMBER", permissions);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                "unused",
                permissions.stream().map(SimpleGrantedAuthority::new).toList()));
  }

  @Test
  void postCreatesChannelWithoutVersionAndReturnsRecordLocation() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    UUID id = channelIds.get(NotificationChannel.LARK);
    var created =
        new NotificationSettingManagementApi.ChannelView(
            id,
            "Lark alerts",
            NotificationChannel.LARK,
            true,
            false,
            false,
            true,
            false,
            null,
            null,
            null,
            1,
            Map.of());
    when(api.createChannel(
            eq(tenant), eq(NotificationChannel.LARK), eq("Lark alerts"), eq(false), anyMap()))
        .thenReturn(created);

    mvc.perform(
            post("/api/v1/notification-settings/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"channel\":\"LARK\",\"name\":\"Lark"
                        + " alerts\",\"enabled\":false,\"tenantId\":\"ignored\",\"connectionConfig\":{\"appToken\":\"secret-ref\"}}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", path(NotificationChannel.LARK)))
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-ref"))));
    verify(api)
        .createChannel(
            tenant,
            NotificationChannel.LARK,
            "Lark alerts",
            false,
            Map.of("appToken", "secret-ref"));
  }

  @Test
  void nameMustBeNonblankAndAtMost150CharactersWhenProvided() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    var json = new ObjectMapper();
    for (String name : List.of("", " ", "x".repeat(151))) {
      mvc.perform(
              post("/api/v1/notification-settings/channels")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json.writeValueAsString(
                          Map.of("channel", "IN_APP", "enabled", true, "name", name))))
          .andExpect(status().isUnprocessableEntity());
      mvc.perform(
              put(path(NotificationChannel.IN_APP))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json.writeValueAsString(
                          Map.of("enabled", false, "version", 1, "name", name))))
          .andExpect(status().isUnprocessableEntity());
    }
    verifyNoInteractions(api);
  }

  @Test
  void postRequiresManagePermissionAndValidChannelConfiguration() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            post("/api/v1/notification-settings/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"IN_APP\",\"enabled\":true}"))
        .andExpect(status().isForbidden());
    actor(tenant, Set.of("notifications.settings.manage"));
    for (String body :
        List.of(
            "{}",
            "{\"channel\":\"EMAIL\"}",
            "{\"channel\":\"EMAIL\",\"enabled\":false,\"connectionConfig\":{\"fromAddress\":\"invalid\"}}",
            "{\"channel\":\"EMAIL\",\"enabled\":false,\"connectionConfig\":{\"appToken\":\"wrong-channel\"}}")) {
      mvc.perform(
              post("/api/v1/notification-settings/channels")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isUnprocessableEntity());
    }
    verifyNoInteractions(api);
  }

  @Test
  void methodAnnotationRejectsPrincipalWithoutGrantedAuthorityBeforeCallingApi() throws Exception {
    var principal =
        new GatewayPrincipal(user, tenant, "USER", "MEMBER", Set.of("notifications.settings.view"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isForbidden());
    verifyNoInteractions(api);
  }

  @Test
  void authenticationTenantAndPermissionAreRequired() throws Exception {
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isUnauthorized());
    // A tenantless identity has no role permissions in GatewayIdentityFilter.
    actor(null, Set.of());
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isForbidden());
    actor(tenant, Set.of());
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isForbidden());
    verifyNoInteractions(api);
  }

  @Test
  void viewingDoesNotGrantWritePermission() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            put(path(NotificationChannel.IN_APP))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":0}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(api);
  }

  @Test
  void tenantIdentityAlwaysComesFromPrincipal() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            get("/api/v1/notification-settings/channels")
                .param("tenantId", UUID.randomUUID().toString())
                .param("userId", UUID.randomUUID().toString()))
        .andExpect(status().isOk());
    verify(api).channels(tenant);
  }

  @Test
  void missingVersionAndInvalidChannelsCannotMutate() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put(path(NotificationChannel.IN_APP))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put("/api/v1/notification-settings/channels/SMS")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channels\":[\"SMS\"],\"version\":0}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(api);
  }

  @Test
  void removedSettingsEndpointsAreNotExposed() throws Exception {
    actor(tenant, Set.of("notifications.settings.view", "notifications.settings.manage"));
    var routes =
        MockMvcBuilders.standaloneSetup(
                new NotificationSettingController(
                    context.getBean(CommandBus.class),
                    context.getBean(QueryBus.class),
                    new ObjectMapper(),
                    validators.getValidator()))
            .build();
    routes.perform(get("/api/v1/notification-settings/features")).andExpect(status().isNotFound());
    routes.perform(get("/api/v1/notification-settings/types")).andExpect(status().isNotFound());
    routes.perform(get("/api/v1/notification-preferences")).andExpect(status().isNotFound());
    verifyNoInteractions(api);
  }

  @Test
  void configurationUsesPathChannelAndAuthenticatedTenant() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put(path(NotificationChannel.LARK))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled":false,"version":0,"tenantId":"ignored",
                     "connectionConfig":{"webhookUrl":"https://open.larksuite.com/hook/test",
                     "appSecretRef":"secrets/lark","appToken":"secrets/app-token"}}
                    """))
        .andExpect(status().isOk());
    verify(api)
        .updateChannel(
            eq(tenant),
            eq(channelIds.get(NotificationChannel.LARK)),
            argThat(
                change ->
                    !change.isEnabled()
                        && change.getVersion() == 0
                        && change
                            .getConnectionConfig()
                            .get("appSecretRef")
                            .equals("secrets/lark")));
  }

  @Test
  void connectionConfigurationRequiresManagePermissionAndValidValues() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            put(path(NotificationChannel.LARK))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":0,\"connectionConfig\":{}}"))
        .andExpect(status().isForbidden());
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put(path(NotificationChannel.LARK))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"appToken\":\" \"}}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put(path(NotificationChannel.LARK))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"chatId\":\"-1\",\"connectionConfig\":{}}"))
        .andExpect(status().isUnprocessableEntity());
    verify(api, never()).updateChannel(any(), any(UUID.class), any());
  }

  @Test
  void eachChannelBindsItsOwnConnectionDto() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    Map<String, String> configurations =
        Map.of(
            "IN_APP", "{}",
            "TELEGRAM",
                "{\"url\":\"https://api.telegram.org\",\"token\":\"secret-ref\",\"chatId\":\"-1\"}",
            "EMAIL",
                "{\"connectionRef\":\"smtp/main\",\"credentialsRef\":\"secrets/smtp\",\"fromAddress\":\"sender@example.com\",\"replyTo\":\"support@example.com\"}",
            "LARK",
                "{\"webhookUrl\":\"https://open.larksuite.com/hook/test\",\"appSecretRef\":\"secrets/lark\",\"appToken\":\"secrets/token\"}");
    var json = new ObjectMapper();
    for (var entry : configurations.entrySet()) {
      mvc.perform(
              put(path(NotificationChannel.valueOf(entry.getKey())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"enabled\":false,\"version\":0,\"connectionConfig\":"
                          + entry.getValue()
                          + "}"))
          .andExpect(status().isOk());
      Map<String, String> expected = json.readValue(entry.getValue(), new TypeReference<>() {});
      verify(api)
          .updateChannel(
              eq(tenant),
              eq(channelIds.get(NotificationChannel.valueOf(entry.getKey()))),
              argThat(change -> change.getConnectionConfig().equals(expected)));
    }
  }

  @Test
  void wrongChannelFieldsAndInvalidEmailDoNotReachApplication() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put(path(NotificationChannel.EMAIL))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"appToken\":\"wrong-channel\"}}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put(path(NotificationChannel.EMAIL))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"fromAddress\":\"invalid\"}}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put(path(NotificationChannel.IN_APP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"token\":\"invalid\"}}"))
        .andExpect(status().isUnprocessableEntity());
    verify(api, never()).updateChannel(any(), any(UUID.class), any());
  }

  @Test
  void channelQueryAndLegacyChangeReachTheirRegisteredHandlers() throws Exception {
    actor(tenant, Set.of("notifications.settings.view", "notifications.settings.manage"));
    mvc.perform(get(path(NotificationChannel.TELEGRAM))).andExpect(status().isOk());
    verify(api).channelById(tenant, channelIds.get(NotificationChannel.TELEGRAM));
    mvc.perform(
            put(path(NotificationChannel.TELEGRAM))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":3,\"botToken\":\"secret\",\"chatId\":\"-1\",\"messageThreadId\":42}"))
        .andExpect(status().isOk());
    verify(api)
        .updateChannel(
            eq(tenant),
            eq(channelIds.get(NotificationChannel.TELEGRAM)),
            argThat(
                change ->
                    !change.isEnabled()
                        && change.getVersion() == 3
                        && "secret".equals(change.getBotToken())
                        && "-1".equals(change.getChatId())
                        && Long.valueOf(42).equals(change.getMessageThreadId())));
  }

  @Test
  void connectionConfigMustBeAnObject() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    for (String value : List.of("[]", "42", "\"secret-value\"")) {
      mvc.perform(
              put(path(NotificationChannel.LARK))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false,\"version\":0,\"connectionConfig\":" + value + "}"))
          .andExpect(status().isBadRequest());
    }
    verify(api, never()).updateChannel(any(), any(UUID.class), any());
  }

  @Test
  void connectionConfigurationIsWriteOnlyAndRedacted() throws Exception {
    var json = new ObjectMapper();
    var request =
        json.readValue(
            "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"appToken\":\"sensitive-ref\"}}",
            NotificationSettingUpdateReq.class);
    assertThat(request.getConnectionConfig().get("appToken").asText()).isEqualTo("sensitive-ref");
    assertThat(json.writeValueAsString(request))
        .doesNotContain("connectionConfig", "sensitive-ref");
    assertThat(request.toString()).doesNotContain("sensitive-ref");
  }

  @Test
  void botTokenIsWriteOnlyAndRedacted() throws Exception {
    var json = new ObjectMapper();
    var request =
        json.readValue(
            "{\"enabled\":false,\"botToken\":\"secret\",\"chatId\":\"-1\",\"version\":0}",
            NotificationSettingUpdateReq.class);
    assertThat(request.getBotToken()).isEqualTo("secret");
    assertThat(json.writeValueAsString(request)).doesNotContain("botToken", "secret");
    assertThat(request.toString()).doesNotContain("secret");
  }
}
