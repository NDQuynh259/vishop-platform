package com.vishop.notification.setting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.modules.setting.application.command.CreateRuleCommandHandler;
import com.vishop.notification.modules.setting.application.command.UpdateRuleCommandHandler;
import com.vishop.notification.modules.setting.application.query.GetRuleQueryHandler;
import com.vishop.notification.modules.setting.application.query.ListRulesQueryHandler;
import com.vishop.notification.modules.setting.application.service.NotificationAlertRuleService;
import com.vishop.notification.modules.setting.presentation.rest.NotificationRuleController;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.security.config.PermissionSecurityAutoConfiguration;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.github.coreplatform.common.web.exception.SecurityExceptionHandler;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

class NotificationRuleHttpTest {
  static final String PATH = "/api/v1/notification-settings/rules";
  final UUID tenant = UUID.randomUUID();
  final UUID ruleId = UUID.randomUUID();
  final NotificationAlertRuleService service = mock(NotificationAlertRuleService.class);
  AnnotationConfigApplicationContext context;
  MockMvc mvc;

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Security {}

  @BeforeEach
  void setup() {
    context = new AnnotationConfigApplicationContext();
    context.register(
        Security.class,
        PermissionSecurityAutoConfiguration.class,
        CqrsAutoConfiguration.class,
        ListRulesQueryHandler.class,
        GetRuleQueryHandler.class,
        CreateRuleCommandHandler.class,
        UpdateRuleCommandHandler.class,
        NotificationRuleController.class);
    context.registerBean(NotificationAlertRuleService.class, () -> service);
    context.refresh();
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(NotificationRuleController.class))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new SecurityExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  void actor(String... permissions) {
    var grants = Set.of(permissions);
    var actor = new GatewayPrincipal(UUID.randomUUID(), tenant, "USER", "OWNER", grants);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                actor, null, grants.stream().map(SimpleGrantedAuthority::new).toList()));
  }

  @Test
  void readsUseTenantFromPrincipalAndRequireViewAuthority() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    actor("notifications.settings.manage");
    mvc.perform(get(PATH)).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    actor("notifications.settings.view");
    var rule =
        new RuleView(ruleId, "STOCK_LOW", "Low stock", true, Set.of(NotificationChannel.IN_APP), 1);
    when(service.rules(tenant)).thenReturn(List.of(rule));
    when(service.ruleById(tenant, ruleId)).thenReturn(rule);
    mvc.perform(get(PATH).param("tenantId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("STOCK_LOW"));
    mvc.perform(get(PATH + "/" + ruleId)).andExpect(status().isOk());
    verify(service).rules(tenant);
    verify(service).ruleById(tenant, ruleId);
  }

  @Test
  void createAndUpdateGoThroughTheirHandlersWithAuthenticatedTenant() throws Exception {
    actor("notifications.settings.manage");
    var rule =
        new RuleView(ruleId, "STOCK_LOW", "Low stock", true, Set.of(NotificationChannel.LARK), 1);
    when(service.createRule(
            tenant, NotificationTypeCode.STOCK_LOW, true, Set.of(NotificationChannel.LARK)))
        .thenReturn(rule);
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tenantId\":\"ignored\",\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[\"LARK\"]}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", PATH + "/" + ruleId))
        .andExpect(jsonPath("$.id").value(ruleId.toString()));
    mvc.perform(
            put(PATH + "/" + ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"SYSTEM_INCIDENT\",\"enabled\":false,\"channels\":[],\"version\":1}"))
        .andExpect(status().isOk());
    verify(service)
        .createRule(tenant, NotificationTypeCode.STOCK_LOW, true, Set.of(NotificationChannel.LARK));
    verify(service)
        .updateRule(
            eq(tenant),
            eq(ruleId),
            argThat(
                change ->
                    !change.isEnabled()
                        && change.getChannels().isEmpty()
                        && change.getVersion() == 1));
  }

  @Test
  void writesRequireManageAndValidateEnumsVersionAndChannels() throws Exception {
    actor("notifications.settings.view");
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[\"IN_APP\"]}"))
        .andExpect(status().isForbidden());
    actor("notifications.settings.manage");
    for (String body :
        List.of(
            "{}",
            "{\"type\":\"STOCK_LOW\",\"enabled\":true}",
            "{\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[null]}")) {
      mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnprocessableEntity());
    }
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"UNKNOWN\",\"enabled\":true,\"channels\":[]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[\"SMS\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(PATH + "/" + ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"channels\":[]}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put(PATH + "/" + ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"channels\":[],\"version\":-1}"))
        .andExpect(status().isUnprocessableEntity());
    verifyNoInteractions(service);
  }
}
