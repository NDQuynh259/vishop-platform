package com.vishop.notification.modules.notification.domain.port;

import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter must join dispatch transaction and obey the database channel constraints. */
public interface NotificationDeliveryRepository {
  void append(List<NotificationDelivery> jobs);

  Optional<NotificationDelivery> findById(UUID tenantId, UUID deliveryId);
}
