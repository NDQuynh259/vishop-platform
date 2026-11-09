package com.vishop.notification.modules.notification.application.port;

import java.util.Set;
import java.util.UUID;

/** Resolves current shop access from a trusted source, never client input. */
@FunctionalInterface
public interface NotificationInboxShopAccess {
  Set<UUID> allowedShops(UUID tenantId, UUID userId);
}
