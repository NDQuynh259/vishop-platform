package com.vishop.notification.modules.setting.application.service;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.modules.setting.application.port.NotificationSettingConnectionPolicy;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared connection validation and response mapping; performs no persistence operations. */
@Component
@RequiredArgsConstructor
public class NotificationChannelSupport {
  private final NotificationSettingConnectionPolicy connections;

  public ChannelView view(UUID tenant, Connection c) {
    var channel = c.channel();
    return new ChannelView(
        c.id(),
        c.name(),
        channel,
        c.configured(),
        c.enabled(),
        connections.available(channel),
        c.credentialsRef() != null
            || (c.connectionConfig() != null && c.connectionConfig().containsKey("appToken")),
        connections.ready(tenant, c),
        c.botUsername(),
        c.chatId(),
        c.messageThreadId(),
        c.version(),
        NotificationConnectionConfiguration.publicConfig(c));
  }

  public Connection configuredConnection(
      UUID tenant,
      NotificationChannel channel,
      boolean enabled,
      Map<String, String> config,
      long version) {
    NotificationConnectionConfiguration.validate(channel, config);
    String connectionRef =
        switch (channel) {
          case TELEGRAM -> config.get("url");
          case EMAIL -> config.get("connectionRef");
          case LARK -> config.get("webhookUrl");
          case IN_APP -> null;
        };
    String credentialsRef =
        switch (channel) {
          case TELEGRAM -> config.get("token");
          case EMAIL -> config.get("credentialsRef");
          case LARK -> config.get("appSecretRef");
          case IN_APP -> null;
        };
    var prepared =
        new Connection(
            channel,
            true,
            enabled,
            connectionRef,
            credentialsRef,
            null,
            config.get("chatId"),
            null,
            version,
            config);
    if (enabled && (!connections.available(channel) || !connections.ready(tenant, prepared))) {
      throw invalid("Channel provider is unavailable or connection is not verified");
    }
    return prepared;
  }

  public String settingName(String name, String fallback) {
    if (name == null) return fallback;
    if (name.isBlank() || name.length() > 150) throw invalid("Invalid setting name");
    return name.trim();
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
