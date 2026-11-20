package com.vishop.notification.modules.setting.infrastructure.persistence.repositories;

import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationSettingTypeJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationSettingTypeRepository
    extends JpaRepository<NotificationSettingTypeJpaEntity, UUID> {
  @Query("""
      select link from NotificationSettingTypeJpaEntity link
      join fetch link.rule
      where link.setting.tenantId = :tenant and link.setting.id in :settings
      """)
  List<NotificationSettingTypeJpaEntity> findSelections(
      @Param("tenant") UUID tenant, @Param("settings") List<UUID> settings);
}
