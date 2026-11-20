package com.vishop.notification.modules.setting.infrastructure.persistence.repositories;

import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationAlertRuleJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationAlertRuleRepository
    extends JpaRepository<NotificationAlertRuleJpaEntity, UUID> {
  List<NotificationAlertRuleJpaEntity> findByTenantIdAndDeletedAtIsNullOrderByType(UUID tenantId);

  Optional<NotificationAlertRuleJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(
      UUID id, UUID tenantId);

  Optional<NotificationAlertRuleJpaEntity> findByTenantIdAndTypeAndDeletedAtIsNull(
      UUID tenantId, String type);

  Optional<NotificationAlertRuleJpaEntity> findByTenantIdAndType(UUID tenantId, String type);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "select r from NotificationAlertRuleJpaEntity r where r.tenantId=:tenant and r.deletedAt is null and r.id in :ids order by r.id")
  List<NotificationAlertRuleJpaEntity> lockActiveByIds(
      @Param("tenant") UUID tenant, @Param("ids") java.util.Set<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select r from NotificationAlertRuleJpaEntity r where r.id=:id and r.tenantId=:tenant and"
          + " r.deletedAt is null")
  Optional<NotificationAlertRuleJpaEntity> lock(
      @Param("tenant") UUID tenantId, @Param("id") UUID ruleId);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO notification_alert_rules(id,tenant_id,type,feature_code) VALUES(:id,:tenant,:type,:feature) ON"
              + " CONFLICT(tenant_id,type) DO NOTHING",
      nativeQuery = true)
  int ensure(@Param("id") UUID id, @Param("tenant") UUID tenantId, @Param("type") String type, @Param("feature") String featureCode);
}
