package com.vishop.notification.modules.setting.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

public interface NotificationSettingManagementApi {
  record ChannelView(
      UUID id,
      String name,
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

  /** Optional config replaces the stored JSON; legacy Telegram fields cannot be mixed with it. */
  @Value
  @ToString(onlyExplicitlyIncluded = true)
  class ChannelUpdate {
    boolean enabled;
    Map<String, String> connectionConfig;
    long version;
    String name;
    String botToken;
    String chatId;
    Long messageThreadId;

    public ChannelUpdate(
        boolean enabled,
        Map<String, String> connectionConfig,
        long version,
        String name,
        String botToken,
        String chatId,
        Long messageThreadId) {
      this.enabled = enabled;
      this.connectionConfig = connectionConfig == null ? null : Map.copyOf(connectionConfig);
      this.version = version;
      this.name = name;
      this.botToken = botToken;
      this.chatId = chatId;
      this.messageThreadId = messageThreadId;
    }

    public ChannelUpdate(boolean enabled, Map<String, String> connectionConfig, long version) {
      this(enabled, connectionConfig, version, null, null, null, null);
    }

    public ChannelUpdate(
        boolean enabled, String botToken, String chatId, Long messageThreadId, long version) {
      this(enabled, null, version, null, botToken, chatId, messageThreadId);
    }
  }

  ChannelView createChannel(
      UUID tenant,
      NotificationChannel channel,
      String name,
      boolean enabled,
      Map<String, String> connectionConfig);

  List<ChannelView> channels(UUID tenant);

  ChannelView channel(UUID tenant, NotificationChannel channel);

  ChannelView channelById(UUID tenant, UUID channelId);

  ChannelView updateChannel(UUID tenant, UUID channelId, ChannelUpdate change);
}
