package com.vishop.notification.modules.inbox.application.service;

import com.vishop.notification.modules.inbox.application.api.NotificationInboxApi;
import com.vishop.notification.modules.inbox.domain.entity.Notification;
import com.vishop.notification.modules.inbox.domain.entity.NotificationRecipient;
import com.vishop.notification.modules.inbox.domain.port.NotificationInboxRepository;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class NotificationInboxApplicationService implements NotificationInboxApi {
  private final NotificationInboxRepository repository;
  private final Clock clock;
  private final Supplier<UUID> ids;

  public NotificationInboxApplicationService(
      NotificationInboxRepository repository, Clock clock, Supplier<UUID> ids) {
    this.repository = Objects.requireNonNull(repository);
    this.clock = Objects.requireNonNull(clock);
    this.ids = Objects.requireNonNull(ids);
  }

  @Override
  public UUID create(NotificationMessage message, Set<UUID> inAppRecipients) {
    var notification = new Notification(ids.get(), message, clock.instant());
    var recipients =
        Set.copyOf(inAppRecipients).stream()
            .map(
                user ->
                    new NotificationRecipient(
                        message.tenantId(),
                        notification.id(),
                        user,
                        notification.createdAt(),
                        null,
                        null))
            .toList();
    repository.append(notification, recipients);
    return notification.id();
  }
}
