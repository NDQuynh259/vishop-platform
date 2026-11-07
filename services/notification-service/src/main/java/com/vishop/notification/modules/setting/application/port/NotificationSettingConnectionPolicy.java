package com.vishop.notification.modules.setting.application.port;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelUpdate;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.UUID;

/**
 * Implement with a secret store and provider verification. No plaintext secrets in PostgreSQL.
 * prepare must stage an immutable connection revision, retain old references until DB commit, and
 * garbage-collect unreferenced revisions on failure. Readiness is bound to that revision.
 */
public interface NotificationSettingConnectionPolicy {
  boolean available(NotificationChannel channel);

  boolean ready(UUID tenant, Connection connection);

  Connection prepare(UUID tenant, Connection current, ChannelUpdate change);
}
