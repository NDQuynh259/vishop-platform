package com.vishop.notification.modules.notification.infrastructure.persistence.mapper;

import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery;
import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationDeliveryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryPersistenceMapper {
  /** New row only: updates must load the persisted entity and retain its template/audit/version. */
  public NotificationDeliveryJpaEntity toNewEntity(NotificationDelivery delivery) {
    var entity = new NotificationDeliveryJpaEntity();
    entity.setId(delivery.id());
    entity.setTenantId(delivery.tenantId());
    entity.setNotificationId(delivery.notificationId());
    entity.setUserId(delivery.userId());
    entity.setChannel(delivery.channel());
    entity.setDestination(delivery.destination());
    entity.setSettingId(delivery.settingId());
    entity.setStatus(delivery.status());
    entity.setAttemptCount(delivery.attemptCount());
    entity.setMaxAttempts(delivery.maxAttempts());
    entity.setNextAttemptAt(delivery.nextAttemptAt());
    entity.setLockToken(delivery.lockToken());
    entity.setLockedUntil(delivery.lockedUntil());
    entity.setSentAt(delivery.sentAt());
    return entity;
  }

  public NotificationDelivery toDomain(NotificationDeliveryJpaEntity entity) {
    return new NotificationDelivery(
        entity.getId(),
        entity.getTenantId(),
        entity.getNotificationId(),
        entity.getUserId(),
        entity.getChannel(),
        entity.getDestination(),
        entity.getStatus(),
        entity.getAttemptCount(),
        entity.getMaxAttempts(),
        entity.getNextAttemptAt(),
        entity.getLockToken(),
        entity.getLockedUntil(),
        entity.getSentAt(),
        entity.getSettingId());
  }
}
