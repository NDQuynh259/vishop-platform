package com.vishop.notification.modules.setting.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationSettingManagementApi {
  record ChannelView(
      NotificationChannel channel,
      boolean configured,
      boolean enabled,
      boolean available,
      boolean credentialsConfigured,
      boolean connectionVerified,
      String botUsername,
      String chatId,
      Long messageThreadId,
      long version,
      Map<String, Object> connectionConfig) {}

  /** Configuration values are opaque secret-store references, never raw credentials. */
  record ConfigurationChange(boolean enabled, Map<String, String> connectionConfig, long version) {
    public ConfigurationChange {
      connectionConfig = Map.copyOf(connectionConfig);
    }

    @Override
    public String toString() {
      return "ConfigurationChange[redacted]";
    }
  }

  /** Write-only secret. Never include it in logs or errors. */
  record ChannelChange(
      boolean enabled, String botToken, String chatId, Long messageThreadId, long version) {
    @Override
    public String toString() {
      return "ChannelChange[redacted]";
    }
  }

  List<ChannelView> channels(UUID tenant);

  ChannelView channel(UUID tenant, NotificationChannel channel);

  ChannelView changeChannel(UUID tenant, NotificationChannel channel, ChannelChange change);

  ChannelView configureChannel(
      UUID tenant, NotificationChannel channel, ConfigurationChange change);
}
