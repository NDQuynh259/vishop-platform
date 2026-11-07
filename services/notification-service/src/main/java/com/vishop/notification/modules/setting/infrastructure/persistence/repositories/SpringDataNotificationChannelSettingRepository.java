package com.vishop.notification.modules.setting.infrastructure.persistence.repositories;

import com.vishop.notification.modules.setting.infrastructure.persistence.entities.*;
import com.vishop.notification.shared.contract.NotificationChannel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationChannelSettingRepository
    extends JpaRepository<NotificationChannelSettingJpaEntity, UUID> {
  Optional<NotificationChannelSettingJpaEntity> findByTenantIdAndChannel(
      UUID tenantId, NotificationChannel channel);

  Optional<NotificationChannelSettingJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(
      UUID id, UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from NotificationChannelSettingJpaEntity c where c.id=:id and c.tenantId=:tenant"
          + " and c.deletedAt is null")
  Optional<NotificationChannelSettingJpaEntity> lockById(
      @Param("tenant") UUID tenant, @Param("id") UUID id);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO notification_settings(id,tenant_id,channel,name)"
              + " VALUES(:id,:tenant,:channel,:channel) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int ensure(@Param("id") UUID id, @Param("tenant") UUID tenant, @Param("channel") String channel);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from NotificationChannelSettingJpaEntity c where c.tenantId=:tenant and"
          + " c.channel=:channel")
  Optional<NotificationChannelSettingJpaEntity> lock(
      @Param("tenant") UUID tenant, @Param("channel") NotificationChannel channel);
}
