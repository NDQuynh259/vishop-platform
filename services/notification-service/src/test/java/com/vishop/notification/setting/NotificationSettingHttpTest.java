package com.vishop.notification.setting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.presentation.rest.*;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationSettingHttpTest {
  final NotificationSettingManagementApi api = mock(NotificationSettingManagementApi.class);
  final UUID tenant = UUID.randomUUID();
  final UUID user = UUID.randomUUID();
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new NotificationSettingController(api))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void actor(UUID tenantId, Set<String> permissions) {
    var principal = new GatewayPrincipal(user, tenantId, "USER", "MEMBER", permissions);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
  }

  @Test
  void authenticationTenantAndPermissionAreRequired() throws Exception {
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isUnauthorized());
    actor(null, Set.of("notifications.settings.view"));
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isForbidden());
    actor(tenant, Set.of());
    mvc.perform(get("/api/v1/notification-settings/channels")).andExpect(status().isForbidden());
    verifyNoInteractions(api);
  }

  @Test
  void viewingDoesNotGrantWritePermission() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            put("/api/v1/notification-settings/channels/IN_APP")
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
            put("/api/v1/notification-settings/channels/IN_APP")
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
    var routes = MockMvcBuilders.standaloneSetup(new NotificationSettingController(api)).build();
    routes.perform(get("/api/v1/notification-settings/features")).andExpect(status().isNotFound());
    routes.perform(get("/api/v1/notification-settings/types")).andExpect(status().isNotFound());
    routes.perform(get("/api/v1/notification-preferences")).andExpect(status().isNotFound());
    verifyNoInteractions(api);
  }

  @Test
  void configurationUsesPathChannelAndAuthenticatedTenant() throws Exception {
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put("/api/v1/notification-settings/channels/LARK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabled":false,"version":0,"tenantId":"ignored",
                     "connectionConfig":{"webhookUrl":"https://open.larksuite.com/hook/test",
                     "appSecretRef":"secrets/lark","appToken":"secrets/app-token"}}
                    """))
        .andExpect(status().isOk());
    verify(api)
        .configureChannel(
            eq(tenant),
            eq(NotificationChannel.LARK),
            argThat(
                change ->
                    !change.enabled()
                        && change.version() == 0
                        && change.connectionConfig().get("appSecretRef").equals("secrets/lark")));
    verify(api, never()).changeChannel(any(), any(), any());
  }

  @Test
  void connectionConfigurationRequiresManagePermissionAndValidValues() throws Exception {
    actor(tenant, Set.of("notifications.settings.view"));
    mvc.perform(
            put("/api/v1/notification-settings/channels/LARK")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":0,\"connectionConfig\":{}}"))
        .andExpect(status().isForbidden());
    actor(tenant, Set.of("notifications.settings.manage"));
    mvc.perform(
            put("/api/v1/notification-settings/channels/LARK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"appToken\":\" \"}}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put("/api/v1/notification-settings/channels/LARK")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"enabled\":false,\"version\":0,\"chatId\":\"-1\",\"connectionConfig\":{}}"))
        .andExpect(status().isUnprocessableEntity());
    verifyNoInteractions(api);
  }

  @Test
  void connectionConfigurationIsWriteOnlyAndRedacted() throws Exception {
    var json = new ObjectMapper();
    var request =
        json.readValue(
            "{\"enabled\":false,\"version\":0,\"connectionConfig\":{\"appToken\":\"sensitive-ref\"}}",
            NotificationSettingRequests.Channel.class);
    assertThat(request.connectionConfig()).containsEntry("appToken", "sensitive-ref");
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
            NotificationSettingRequests.Channel.class);
    assertThat(request.botToken()).isEqualTo("secret");
    assertThat(json.writeValueAsString(request)).doesNotContain("botToken", "secret");
    assertThat(request.toString()).doesNotContain("secret");
  }
}
