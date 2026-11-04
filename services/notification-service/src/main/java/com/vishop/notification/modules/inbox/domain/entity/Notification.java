package com.vishop.notification.modules.inbox.domain.entity;

import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published payload is immutable. Read/archive state belongs to the recipient. */
public record Notification(UUID id, NotificationMessage message, Instant createdAt) {
  public Notification {
    Objects.requireNonNull(id);
    Objects.requireNonNull(message);
    Objects.requireNonNull(createdAt);
  }
}
