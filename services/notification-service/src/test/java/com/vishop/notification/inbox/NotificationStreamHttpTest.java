package com.vishop.notification.inbox;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import com.vishop.notification.modules.notification.presentation.rest.controller.NotificationStreamController;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.github.coreplatform.common.web.exception.SecurityExceptionHandler;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationStreamHttpTest {
  static final String PATH = "/api/v1/notifications/stream";
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
  AnnotationConfigApplicationContext context;
  NotificationSseStreams streams;
  MockMvc mvc;

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Security {}

  @BeforeEach
  void setup() {
    context = new AnnotationConfigApplicationContext();
    context.register(Security.class, NotificationStreamController.class);
    context.registerBean(
        NotificationSseStreams.class, () -> new NotificationSseStreams(Clock.systemUTC()));
    context.refresh();
    streams = context.getBean(NotificationSseStreams.class);
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(NotificationStreamController.class))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new SecurityExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  void authenticate() {
    var principal = new GatewayPrincipal(user, tenant, "USER", "MEMBER", Set.of("inventory.view"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @Test
  void anonymousCannotSubscribe() throws Exception {
    mvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserMustSelectATenantBeforeSubscribing() throws Exception {
    var principal = new GatewayPrincipal(user, null, "ADMIN", null, Set.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    mvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad_request"))
        .andExpect(header().doesNotExist("Retry-After"));
  }

  @Test
  void streamsOnlyMinimalPayloadAndIgnoresClientScopeParameters() throws Exception {
    authenticate();
    var result =
        mvc.perform(
                get(PATH)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .param("tenantId", UUID.randomUUID().toString())
                    .param("userId", UUID.randomUUID().toString())
                    .param("permissions", "finance.view")
                    .header("Last-Event-ID", "untrusted"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              assertThat(result.getResponse().getContentAsString())
                  .contains("event:ready", "retry:3000");
              content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM).match(result);
              header().string("Cache-Control", "no-store").match(result);
              header().string("X-Accel-Buffering", "no").match(result);
            });
    streams.broadcastLocal(new NotificationCreated(id, tenant, Set.of(user), "inventory.view"));
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () ->
                assertThat(result.getResponse().getContentAsString())
                    .contains("event:notification.created", id.toString(), tenant.toString())
                    .doesNotContain(
                        user.toString(), "inventory.view", "recipients", "requiredPermission"));
    streams.close();
    mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
  }

  @Test
  void tooManyConnectionsReturn429EvenWithGlobalExceptionAdvice() throws Exception {
    authenticate();
    for (int i = 0; i < 5; i++) mvc.perform(get(PATH)).andExpect(status().isOk());
    mvc.perform(get(PATH))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("too_many_requests"))
        .andExpect(jsonPath("$.instance").value(PATH))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(header().string("Retry-After", "3"));
  }

  @Test
  void shutdownUsesTheSharedUnavailableResponseEvenWhenClientAcceptsOnlySse() throws Exception {
    authenticate();
    streams.close();
    mvc.perform(get(PATH).accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("service_unavailable"))
        .andExpect(header().string("Retry-After", "3"));
  }
}
