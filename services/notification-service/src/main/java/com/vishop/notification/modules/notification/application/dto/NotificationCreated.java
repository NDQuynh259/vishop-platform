package com.vishop.notification.modules.notification.application.dto;

import java.util.Set;
import java.util.UUID;

/** Internal signal; recipient IDs and permission must never be serialized to the client. */
public record NotificationCreated(
    UUID notificationId, UUID tenantId, Set<UUID> recipients, String requiredPermission) {
  public NotificationCreated {
    recipients = Set.copyOf(recipients);
  }
}
