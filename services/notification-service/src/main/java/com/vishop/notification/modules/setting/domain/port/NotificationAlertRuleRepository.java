package com.vishop.notification.modules.setting.domain.port;

import com.vishop.notification.modules.setting.domain.entity.NotificationAlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationAlertRuleRepository {
  List<NotificationAlertRule> findAll(UUID tenantId);

  Optional<NotificationAlertRule> findByType(UUID tenantId, String type);

  NotificationAlertRule findById(UUID tenantId, UUID ruleId);

  NotificationAlertRule create(UUID tenantId, NotificationAlertRule rule);

  NotificationAlertRule update(UUID tenantId, NotificationAlertRule rule, long expectedVersion);
}
