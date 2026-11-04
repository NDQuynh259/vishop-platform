package com.vishop.notification.modules.delivery.domain.port;

import com.vishop.notification.modules.delivery.domain.entity.DeliveryJob;
import com.vishop.notification.shared.contract.NotificationChannel;
import com.vishop.notification.shared.contract.NotificationMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adapter must join dispatch transaction and obey the database channel constraints. */
public interface NotificationDeliveryRepository {
  void append(List<DeliveryJob> jobs);

  /** Lock/upsert cooldown keys atomically. Never add users/channels, no provider side effects. */
  Map<UUID, Set<NotificationChannel>> reserveAllowedRoutes(
      NotificationMessage message, Map<UUID, Set<NotificationChannel>> routes, Instant now);
}
