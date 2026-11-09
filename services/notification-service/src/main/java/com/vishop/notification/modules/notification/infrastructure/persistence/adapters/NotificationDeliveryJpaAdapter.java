package com.vishop.notification.modules.notification.infrastructure.persistence.adapters;

import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery;
import com.vishop.notification.modules.notification.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.modules.notification.infrastructure.persistence.mapper.NotificationDeliveryPersistenceMapper;
import com.vishop.notification.modules.notification.infrastructure.persistence.repositories.SpringDataNotificationDeliveryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDeliveryJpaAdapter implements NotificationDeliveryRepository {
  private final SpringDataNotificationDeliveryRepository deliveries;
  private final NotificationDeliveryPersistenceMapper mapper;

  @Override
  @Transactional
  public void append(List<NotificationDelivery> jobs) {
    deliveries.saveAllAndFlush(jobs.stream().map(mapper::toNewEntity).toList());
  }

  @Override
  public Optional<NotificationDelivery> findById(UUID tenantId, UUID deliveryId) {
    return deliveries.findByTenantIdAndId(tenantId, deliveryId).map(mapper::toDomain);
  }
}
