package com.vishop.notification.modules.setting.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.*;
import com.vishop.notification.modules.setting.infrastructure.persistence.model.*;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSettingPersistenceMapper {
  private final ObjectMapper json;

  public Connection connection(
      NotificationChannel channel, NotificationChannelSettingJpaEntity entity) {
    if (entity == null || entity.getDeletedAt() != null)
      return new Connection(
          channel,
          false,
          channel == NotificationChannel.IN_APP,
          null,
          null,
          null,
          null,
          null,
          entity == null ? 0 : entity.getVersion());

    if (entity.getChannel() != channel) {
      throw new IllegalArgumentException("Connection channel does not match the setting channel");
    }
    var config = entity.getConnectionConfig();
    String connectionRef = null;
    String credentialsRef = null;
    String botUsername = null;
    String chatId = null;
    Long messageThreadId = null;

    switch (entity.getChannel()) {
      case TELEGRAM -> {
        var tele = json.convertValue(config, TelegramConfig.class);
        connectionRef = tele.url();
        credentialsRef = tele.token();
        chatId = tele.chatId();
      }
      case EMAIL -> {
        var email = json.convertValue(config, EmailConfig.class);
        connectionRef = email.connectionRef();
        credentialsRef = email.credentialsRef();
      }
      case LARK -> {
        var lark = json.convertValue(config, LarkConfig.class);
        connectionRef = lark.webhookUrl();
        credentialsRef = lark.appSecretRef();
      }
      case IN_APP -> {}
    }

    return new Connection(
        channel,
        true,
        entity.isEnabled(),
        connectionRef,
        credentialsRef,
        botUsername,
        chatId,
        messageThreadId,
        entity.getVersion(),
        configurationValues(entity),
        entity.getId(),
        entity.getName());
  }

  public void copyConnection(NotificationChannelSettingJpaEntity entity, Connection connection) {
    Objects.requireNonNull(entity.getChannel(), "Setting channel is required");
    if (entity.getChannel() != connection.channel()) {
      throw new IllegalArgumentException("Connection channel does not match the setting channel");
    }
    entity.setName(connection.name());
    entity.setEnabled(connection.enabled());
    Object config;
    if (connection.connectionConfig() != null) {
      config =
          switch (entity.getChannel()) {
            case TELEGRAM -> json.convertValue(connection.connectionConfig(), TelegramConfig.class);
            case EMAIL -> json.convertValue(connection.connectionConfig(), EmailConfig.class);
            case LARK -> json.convertValue(connection.connectionConfig(), LarkConfig.class);
            case IN_APP -> new InAppConfig();
          };
    } else {
      config =
          switch (entity.getChannel()) {
            case TELEGRAM ->
                new TelegramConfig(
                    connection.connectionRef(), connection.credentialsRef(), connection.chatId());
            case EMAIL -> {
              var current = json.convertValue(entity.getConnectionConfig(), EmailConfig.class);
              yield new EmailConfig(
                  connection.connectionRef(),
                  connection.credentialsRef(),
                  current.fromAddress(),
                  current.replyTo());
            }
            case LARK ->
                new LarkConfig(
                    connection.connectionRef(),
                    connection.credentialsRef(),
                    json.convertValue(entity.getConnectionConfig(), LarkConfig.class).appToken());
            case IN_APP -> new InAppConfig();
          };
    }
    entity.setConnectionConfig(
        json.convertValue(config, new TypeReference<Map<String, Object>>() {}));
    entity.restore();
  }

  private Map<String, String> configurationValues(NotificationChannelSettingJpaEntity entity) {
    Object config =
        switch (entity.getChannel()) {
          case TELEGRAM -> json.convertValue(entity.getConnectionConfig(), TelegramConfig.class);
          case EMAIL -> json.convertValue(entity.getConnectionConfig(), EmailConfig.class);
          case LARK -> json.convertValue(entity.getConnectionConfig(), LarkConfig.class);
          case IN_APP -> new InAppConfig();
        };
    Map<String, String> values =
        json.convertValue(config, new TypeReference<Map<String, String>>() {});
    values.values().removeIf(Objects::isNull);
    return values;
  }
}
