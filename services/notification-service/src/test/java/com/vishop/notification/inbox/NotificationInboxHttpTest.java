package com.vishop.notification.inbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vishop.notification.modules.inbox.application.command.*;
import com.vishop.notification.modules.inbox.application.dto.*;
import com.vishop.notification.modules.inbox.application.query.*;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import com.vishop.notification.modules.inbox.presentation.rest.NotificationController;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.cqrs.CqrsAutoConfiguration;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.github.coreplatform.common.web.exception.SecurityExceptionHandler;
import io.github.coreplatform.common.web.pagination.PaginationRequestArgumentResolver;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationInboxHttpTest {
  static final String PATH = "/api/v1/notifications";
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();
  final NotificationInboxApplicationService service =
      mock(NotificationInboxApplicationService.class);
  MockMvc mvc;
  AnnotationConfigApplicationContext context;

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Security {}

  @BeforeEach
  void setup() {
    context = new AnnotationConfigApplicationContext();
    context.register(
        Security.class,
        CqrsAutoConfiguration.class,
        NotificationController.class,
        ListInboxQueryHandler.class,
        GetNotificationQueryHandler.class,
        UnreadCountQueryHandler.class,
        MarkNotificationReadCommandHandler.class,
        MarkAllNotificationsReadCommandHandler.class,
        ArchiveNotificationCommandHandler.class);
    context.registerBean(NotificationInboxApplicationService.class, () -> service);
    context.refresh();
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(NotificationController.class))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new PaginationRequestArgumentResolver())
            .setControllerAdvice(new SecurityExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  void actor() {
    var principal = new GatewayPrincipal(user, tenant, "USER", "MEMBER", Set.of("inventory.view"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  NotificationView view() {
    return new NotificationView(
        id,
        "STOCK_LOW",
        "INVENTORY",
        "BUSINESS",
        NotificationMessage.Severity.WARNING,
        "Low stock",
        "Check",
        "/inventory",
        null,
        Instant.now(),
        Instant.now(),
        null);
  }

  @Test
  void allInboxEndpointsRequireAuthenticationBeforeCallingService() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    mvc.perform(get(PATH + "/unread-count")).andExpect(status().isUnauthorized());
    mvc.perform(get(PATH + "/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(patch(PATH + "/" + id + "/read")).andExpect(status().isUnauthorized());
    mvc.perform(
            post(PATH + "/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"before\":\"2026-11-07T12:00:00Z\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete(PATH + "/" + id)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void identityAndPermissionsComeFromPrincipalAndListIsPaginated() throws Exception {
    actor();
    when(service.list(any(), any())).thenReturn(PagedResult.ofCursor(List.of(view()), false, null));
    when(service.unreadCount(any())).thenReturn(new UnreadCount(1));
    when(service.getById(any(), eq(id))).thenReturn(view());
    mvc.perform(
            get(PATH)
                .param("tenantId", UUID.randomUUID().toString())
                .param("userId", UUID.randomUUID().toString())
                .param("permissions", "finance.settlement.view")
                .param("limit", "10")
                .param("read", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(id.toString()));
    verify(service)
        .list(
            argThat(
                a ->
                    a.getTenantId().equals(tenant)
                        && a.getUserId().equals(user)
                        && a.getPermissions().equals(Set.of("inventory.view"))),
            argThat(f -> f.getLimit() == 10 && Boolean.FALSE.equals(f.getRead())));
    mvc.perform(get(PATH + "/unread-count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1));
    mvc.perform(get(PATH + "/" + id)).andExpect(status().isOk());
  }

  @Test
  void sharedPaginationSelectsOffsetOrCursorAndDefaults() throws Exception {
    actor();
    when(service.list(any(), any())).thenReturn(PagedResult.ofOffset(List.of(view()), 2, 10, 25));
    mvc.perform(get(PATH).param("page", "2").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageInfo.page").value(2))
        .andExpect(jsonPath("$.pageInfo.total").value(25))
        .andExpect(jsonPath("$.pageInfo.totalPages").value(3));
    verify(service)
        .list(any(), argThat(f -> f.getPagination().page() == 2 && f.getCursorParams() == null));
    when(service.list(any(), any()))
        .thenReturn(PagedResult.ofCursor(List.of(view()), true, "next"));
    mvc.perform(get(PATH).param("pageMode", "cursor").param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageInfo.nextCursor").value("next"))
        .andExpect(jsonPath("$.pageInfo.page").doesNotExist());
    verify(service)
        .list(
            any(), argThat(f -> f.getPagination() == null && f.getCursorParams().cursor() == null));
    mvc.perform(
            get(PATH)
                .param("pageMode", "cursor")
                .param("cursor", "token")
                .param("page", "2")
                .param("limit", "100"))
        .andExpect(status().isOk());
    verify(service)
        .list(
            any(),
            argThat(
                f ->
                    f.getPagination() == null
                        && "token".equals(f.getCursor())
                        && f.getLimit() == 100));
    mvc.perform(get(PATH).param("cursor", " ").param("page", "").param("limit", ""))
        .andExpect(status().isOk());
    verify(service)
        .list(
            any(),
            argThat(
                f ->
                    f.getPagination() != null
                        && f.getPagination().page() == 1
                        && f.getLimit() == 20));
  }

  @Test
  void recipientCommandsGoThroughTheBusWithoutPublicPayloadCreationOrEditing() throws Exception {
    actor();
    when(service.markRead(any(), eq(id))).thenReturn(view());
    when(service.markAllRead(any(), any())).thenReturn(new UpdatedCount(2));
    mvc.perform(patch(PATH + "/" + id + "/read")).andExpect(status().isOk());
    mvc.perform(
            post(PATH + "/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"before\":\"2026-11-07T12:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedCount").value(2));
    mvc.perform(delete(PATH + "/" + id)).andExpect(status().isNoContent());
    verify(service)
        .markRead(
            argThat(a -> a.getUserId().equals(user) && a.getTenantId().equals(tenant)), eq(id));
    verify(service).archive(argThat(a -> a.getUserId().equals(user)), eq(id));
    mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(
            put(PATH + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\"}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void invalidPaginationAndReadAllBodiesDoNotReachTheService() throws Exception {
    actor();
    mvc.perform(get(PATH).param("page", "0")).andExpect(status().isUnprocessableEntity());
    mvc.perform(get(PATH).param("limit", "0")).andExpect(status().isUnprocessableEntity());
    mvc.perform(get(PATH).param("limit", "101")).andExpect(status().isUnprocessableEntity());
    mvc.perform(get(PATH).param("limit", "abc")).andExpect(status().isBadRequest());
    mvc.perform(get(PATH).param("page", "abc")).andExpect(status().isBadRequest());
    mvc.perform(get(PATH).param("pageMode", "unknown")).andExpect(status().isUnprocessableEntity());
    mvc.perform(post(PATH + "/read-all").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get(PATH + "/invalid-id")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
