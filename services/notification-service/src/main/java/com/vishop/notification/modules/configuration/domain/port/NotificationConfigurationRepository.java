package com.vishop.notification.modules.configuration.domain.port;

import com.vishop.notification.modules.configuration.domain.entity.TenantConfiguration;
import com.vishop.notification.modules.configuration.domain.entity.UserPreference;
import java.util.Optional;
import java.util.UUID;

/** Load only non-deleted configuration. Provider readiness must gate enabledChannels. */
public interface NotificationConfigurationRepository {
  TenantConfiguration loadTenant(UUID tenantId, String type, String featureCode);

  Optional<UserPreference> findPreference(UUID tenantId, UUID userId, String type);
}
