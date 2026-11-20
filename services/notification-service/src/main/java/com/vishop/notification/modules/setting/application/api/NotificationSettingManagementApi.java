package com.vishop.notification.modules.setting.application.api;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

public interface NotificationSettingManagementApi {
  record ChannelRulesView(UUID settingId, long version, Set<UUID> ruleIds) {}

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
      Map<String, Object> connectionConfig,
      Set<NotificationTypeCode> allowedTypes) {
    public ChannelView(
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
        Map<String, Object> connectionConfig) {
      this(
          id,
          name,
          channel,
          configured,
          enabled,
          available,
          credentialsConfigured,
          connectionVerified,
          botUsername,
          chatId,
          messageThreadId,
          version,
          connectionConfig,
          Set.of());
    }
  }

  /** Optional config replaces the stored JSON; legacy Telegram fields cannot be mixed with it. */
  @Value
  @ToString(onlyExplicitlyIncluded = true)
  class ChannelUpdate {
    boolean enabled;
    Map<String, String> connectionConfig;
    long version;
    String name;
    Set<NotificationTypeCode> allowedTypes;
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
      this(enabled, connectionConfig, version, name, botToken, chatId, messageThreadId, null);
    }

    public ChannelUpdate(
        boolean enabled,
        Map<String, String> connectionConfig,
        long version,
        String name,
        String botToken,
        String chatId,
        Long messageThreadId,
        Set<NotificationTypeCode> allowedTypes) {
      this.allowedTypes = allowedTypes == null ? null : Set.copyOf(allowedTypes);
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

  ChannelView createChannel(
      UUID tenant,
      NotificationChannel channel,
      String name,
      boolean enabled,
      Map<String, String> connectionConfig,
      Set<NotificationTypeCode> allowedTypes);

  List<ChannelView> channels(UUID tenant);

  ChannelView channel(UUID tenant, NotificationChannel channel);

  ChannelView channelById(UUID tenant, UUID channelId);

  ChannelView updateChannel(UUID tenant, UUID channelId, ChannelUpdate change);

  ChannelRulesView channelRules(UUID tenant, UUID settingId);

  ChannelRulesView replaceChannelRules(
      UUID tenant, UUID settingId, long version, Set<UUID> ruleIds);
}
