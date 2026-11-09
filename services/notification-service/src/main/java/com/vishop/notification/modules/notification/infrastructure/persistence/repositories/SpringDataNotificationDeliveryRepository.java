package com.vishop.notification.modules.notification.infrastructure.persistence.repositories;

import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationDeliveryJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationDeliveryRepository
    extends JpaRepository<NotificationDeliveryJpaEntity, UUID> {
  Optional<NotificationDeliveryJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
