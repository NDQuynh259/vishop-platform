package com.vishop.notification.modules.notification.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryApi {
  record Target(UUID userId, NotificationChannel channel, String destination, UUID settingId) {
    public Target(UUID userId, NotificationChannel channel, String destination) {
      this(userId, channel, destination, null);
    }
  }

  List<UUID> enqueue(UUID tenantId, UUID notificationId, List<Target> targets);
}
