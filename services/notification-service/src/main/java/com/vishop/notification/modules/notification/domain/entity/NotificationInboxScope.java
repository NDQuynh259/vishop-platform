package com.vishop.notification.modules.notification.domain.entity;

import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationInboxScope {
  UUID tenantId;
  UUID userId;
  Set<String> permissions;

  public NotificationInboxScope(UUID tenantId, UUID userId, Set<String> permissions) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.permissions = Set.copyOf(permissions);
  }
}
