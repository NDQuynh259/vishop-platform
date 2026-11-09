package com.vishop.notification.modules.notification.application.service;

import com.vishop.notification.modules.notification.application.api.NotificationApi;
import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.InboxFilter;
import com.vishop.notification.modules.notification.application.dto.NotificationView;
import com.vishop.notification.modules.notification.application.dto.UnreadCount;
import com.vishop.notification.modules.notification.application.dto.UpdatedCount;
import com.vishop.notification.modules.notification.application.port.NotificationInboxShopAccess;
import com.vishop.notification.modules.notification.domain.entity.Notification;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxEntry;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxFilter;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxScope;
import com.vishop.notification.modules.notification.domain.entity.NotificationRecipient;
import com.vishop.notification.modules.notification.domain.port.NotificationRepository;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService implements NotificationApi {
  private final NotificationRepository repository;
  private final Clock clock;
  private final Supplier<UUID> ids;
  private final NotificationInboxShopAccess shops;

  @Transactional
  public UUID create(NotificationMessage message, Set<UUID> inAppRecipients) {
    validate(message);
    var normalized =
        new NotificationMessage(
            message.tenantId(),
            message.sourceEventId(),
            message.type(),
            message.subjectType(),
            message.subjectId(),
            message.requiredPermission(),
            message.shopId(),
            message.severity(),
            message.title(),
            message.body(),
            message.actionUrl(),
            message.occurredAt().truncatedTo(ChronoUnit.MICROS));
    var notification = new Notification(ids.get(), normalized, now());
    var recipients =
        Set.copyOf(inAppRecipients).stream()
            .map(
                user ->
                    new NotificationRecipient(
                        ids.get(),
                        message.tenantId(),
                        notification.id(),
                        user,
                        notification.createdAt(),
                        null,
                        null))
            .toList();
    return repository.append(notification, recipients);
  }

  public PagedResult<NotificationView> list(InboxActor actor, InboxFilter filter) {
    if ((filter.getPagination() == null) == (filter.getCursorParams() == null))
      throw invalid("Choose exactly one pagination mode");
    long offset = filter.getPagination() == null ? 0 : filter.getPagination().offset();
    if (offset > Integer.MAX_VALUE) throw new BadRequestException("Pagination offset is too large");
    Instant before = null;
    UUID beforeId = null;
    if (filter.getCursor() != null) {
      try {
        if (filter.getCursor().length() > 256) throw new IllegalArgumentException();
        String decoded =
            new String(Base64.getUrlDecoder().decode(filter.getCursor()), StandardCharsets.UTF_8);
        var parts = decoded.split("\\|", -1);
        if (parts.length != 2) throw new IllegalArgumentException();
        before = Instant.parse(parts[0]);
        beforeId = UUID.fromString(parts[1]);
        if (!beforeId.toString().equals(parts[1])) throw new IllegalArgumentException();
      } catch (RuntimeException ex) {
        throw new BadRequestException("Invalid notification cursor");
      }
    }
    var query =
        new NotificationInboxFilter(
            filter.getType() == null ? null : filter.getType().code(),
            filter.getSeverity(),
            filter.getRead(),
            filter.getShopId(),
            before,
            beforeId,
            filter.getLimit() + (filter.getPagination() == null ? 1 : 0),
            (int) offset);
    var scope = scope(actor);
    var rows = repository.findAll(scope, query);
    if (filter.getPagination() != null) {
      return PagedResult.ofOffset(
          rows.stream().map(this::view).toList(),
          filter.getPagination().page(),
          filter.getLimit(),
          repository.count(scope, query));
    }
    boolean hasNext = rows.size() > filter.getLimit();
    var page = rows.stream().limit(filter.getLimit()).toList();
    String cursor = null;
    if (hasNext) {
      var last = page.getLast().getNotification();
      cursor =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.createdAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
    }
    return PagedResult.ofCursor(page.stream().map(this::view).toList(), hasNext, cursor);
  }

  public NotificationView getById(InboxActor actor, UUID notificationId) {
    return view(
        repository
            .findById(scope(actor), notificationId)
            .orElseThrow(() -> missing(notificationId)));
  }

  public UnreadCount unreadCount(InboxActor actor) {
    return new UnreadCount(repository.unreadCount(scope(actor)));
  }

  @Transactional
  public NotificationView markRead(InboxActor actor, UUID notificationId) {
    return view(repository.markRead(scope(actor), notificationId, now()));
  }

  @Transactional
  public UpdatedCount markAllRead(InboxActor actor, Instant before) {
    Instant at = now();
    if (before == null || before.isAfter(at))
      throw invalid("Read-all cutoff must not be in the future");
    return new UpdatedCount(
        repository.markAllRead(scope(actor), before.truncatedTo(ChronoUnit.MICROS), at));
  }

  @Transactional
  public void archive(InboxActor actor, UUID notificationId) {
    repository.archive(scope(actor), notificationId, now());
  }

  private NotificationInboxScope scope(InboxActor actor) {
    return new NotificationInboxScope(
        actor.getTenantId(),
        actor.getUserId(),
        actor.getPermissions(),
        shops.allowedShops(actor.getTenantId(), actor.getUserId()));
  }

  private NotificationView view(NotificationInboxEntry entry) {
    var notification = entry.getNotification();
    var message = notification.message();
    var type = NotificationTypeCode.fromCode(message.type());
    return new NotificationView(
        notification.id(),
        message.type(),
        type.map(t -> t.feature().code()).orElse(null),
        type.map(t -> t.category().name()).orElse(null),
        message.severity(),
        message.title(),
        message.body(),
        message.actionUrl(),
        message.shopId(),
        message.occurredAt(),
        notification.createdAt(),
        entry.getReadAt());
  }

  private void validate(NotificationMessage message) {
    if (NotificationTypeCode.fromCode(message.type()).isEmpty())
      throw invalid("Unknown notification type");
    if (message.sourceEventId().length() > 100
        || message.type().length() > 100
        || message.subjectType().length() > 100
        || message.subjectId().length() > 200
        || message.requiredPermission().length() > 150
        || message.title().length() > 300) throw invalid("Notification field is too long");
    if (message.actionUrl() != null) {
      try {
        var uri = URI.create(message.actionUrl());
        if (message.actionUrl().length() > 2000
            || !message.actionUrl().startsWith("/")
            || message.actionUrl().startsWith("//")
            || message.actionUrl().contains("\\")
            || uri.isAbsolute()
            || uri.getRawAuthority() != null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException ex) {
        throw invalid("Notification actionUrl must be a local application path");
      }
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static EntityNotFoundException missing(UUID id) {
    return new EntityNotFoundException("Notification", id);
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
