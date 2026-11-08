package com.vishop.notification.modules.inbox.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationRecipient(
    UUID id,
    UUID tenantId,
    UUID notificationId,
    UUID userId,
    Instant createdAt,
    Instant readAt,
    Instant deletedAt) {
  public NotificationRecipient {
    Objects.requireNonNull(id);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(notificationId);
    Objects.requireNonNull(userId);
    Objects.requireNonNull(createdAt);
    if ((readAt != null && readAt.isBefore(createdAt))
        || (deletedAt != null && deletedAt.isBefore(createdAt)))
      throw new IllegalArgumentException("Invalid recipient timestamp");
  }

  public NotificationRecipient markRead(Instant at) {
    Objects.requireNonNull(at);
    if (deletedAt != null) throw new IllegalStateException("Recipient is archived");
    return readAt == null
        ? new NotificationRecipient(id, tenantId, notificationId, userId, createdAt, at, null)
        : this;
  }

  public NotificationRecipient archive(Instant at) {
    Objects.requireNonNull(at);
    return deletedAt == null
        ? new NotificationRecipient(id, tenantId, notificationId, userId, createdAt, readAt, at)
        : this;
  }
}
