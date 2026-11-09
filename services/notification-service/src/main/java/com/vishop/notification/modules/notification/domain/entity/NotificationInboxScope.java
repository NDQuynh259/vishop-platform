package com.vishop.notification.modules.notification.domain.entity;

import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class NotificationInboxScope {
  UUID tenantId;
  UUID userId;
  Set<String> permissions;
  Set<UUID> shopIds;

  public NotificationInboxScope(
      UUID tenantId, UUID userId, Set<String> permissions, Set<UUID> shopIds) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.permissions = Set.copyOf(permissions);
    this.shopIds = Set.copyOf(shopIds);
  }
}
