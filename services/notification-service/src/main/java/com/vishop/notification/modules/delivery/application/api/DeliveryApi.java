package com.vishop.notification.modules.delivery.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface DeliveryApi {
  record Target(UUID userId, NotificationChannel channel, String destination) {}

  Map<UUID, Set<NotificationChannel>> reserveAllowedRoutes(
      NotificationMessage message, Map<UUID, Set<NotificationChannel>> routes);

  List<UUID> enqueue(UUID tenantId, UUID notificationId, List<Target> targets);
}
