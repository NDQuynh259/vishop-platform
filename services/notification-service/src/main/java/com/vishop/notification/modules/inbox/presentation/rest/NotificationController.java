package com.vishop.notification.modules.inbox.presentation.rest;

import com.vishop.notification.modules.inbox.application.command.ArchiveNotificationCommand;
import com.vishop.notification.modules.inbox.application.command.MarkAllNotificationsReadCommand;
import com.vishop.notification.modules.inbox.application.command.MarkNotificationReadCommand;
import com.vishop.notification.modules.inbox.application.dto.InboxActor;
import com.vishop.notification.modules.inbox.application.dto.InboxFilter;
import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import com.vishop.notification.modules.inbox.application.dto.UnreadCount;
import com.vishop.notification.modules.inbox.application.dto.UpdatedCount;
import com.vishop.notification.modules.inbox.application.query.GetNotificationQuery;
import com.vishop.notification.modules.inbox.application.query.ListInboxQuery;
import com.vishop.notification.modules.inbox.application.query.UnreadCountQuery;
import com.vishop.notification.modules.inbox.presentation.rest.dto.NotificationInboxListReq;
import com.vishop.notification.modules.inbox.presentation.rest.dto.NotificationReadAllReq;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationController {
  private final CommandBus commands;
  private final QueryBus queries;

  @GetMapping
  public ListEnvelope<NotificationView> list(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @Valid @ModelAttribute NotificationInboxListReq request) {
    return ListEnvelope.from(
        ApiPaths.V1 + "/notifications",
        queries.dispatch(
            new ListInboxQuery(
                toInboxActor(actor),
                new InboxFilter(
                    request.getType(),
                    request.getSeverity(),
                    request.getRead(),
                    request.getShopId(),
                    request.getLimit(),
                    request.getCursor()))));
  }

  @GetMapping("/unread-count")
  public UnreadCount unreadCount(@AuthenticationPrincipal GatewayPrincipal actor) {
    return queries.dispatch(new UnreadCountQuery(toInboxActor(actor)));
  }

  @GetMapping("/{notificationId}")
  public NotificationView getById(
      @AuthenticationPrincipal GatewayPrincipal actor, @PathVariable UUID notificationId) {
    return queries.dispatch(new GetNotificationQuery(toInboxActor(actor), notificationId));
  }

  @PatchMapping("/{notificationId}/read")
  public NotificationView markRead(
      @AuthenticationPrincipal GatewayPrincipal actor, @PathVariable UUID notificationId) {
    return commands.dispatch(new MarkNotificationReadCommand(toInboxActor(actor), notificationId));
  }

  @PostMapping("/read-all")
  public UpdatedCount markAllRead(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @Valid @RequestBody NotificationReadAllReq request) {
    return commands.dispatch(
        new MarkAllNotificationsReadCommand(toInboxActor(actor), request.getBefore()));
  }

  @DeleteMapping("/{notificationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(
      @AuthenticationPrincipal GatewayPrincipal actor, @PathVariable UUID notificationId) {
    commands.dispatch(new ArchiveNotificationCommand(toInboxActor(actor), notificationId));
  }

  private InboxActor toInboxActor(GatewayPrincipal principal) {

    return new InboxActor(principal.tenantId(), principal.userId(), principal.permissions());
  }
}
