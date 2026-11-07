package com.vishop.notification.modules.inbox.application.dto;

import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class InboxActor {
  UUID tenantId;
  UUID userId;
  Set<String> permissions;

  public InboxActor(UUID tenantId, UUID userId, Set<String> permissions) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.permissions = Set.copyOf(permissions);
  }
}
