package com.vishop.notification.modules.setting.application.service;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ConfigurationChange;
import com.vishop.notification.modules.setting.application.port.NotificationSettingConnectionPolicy;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class NotificationSettingManagementService implements NotificationSettingManagementApi {
  private final NotificationSettingManagementRepository repository;
  private final NotificationSettingConnectionPolicy connections;

  public NotificationSettingManagementService(
      NotificationSettingManagementRepository repository,
      NotificationSettingConnectionPolicy connections) {
    this.repository = repository;
    this.connections = connections;
  }

  public List<ChannelView> channels(UUID tenant) {
    return Arrays.stream(NotificationChannel.values()).map(c -> channel(tenant, c)).toList();
  }

  public ChannelView channel(UUID tenant, NotificationChannel channel) {
    var c = repository.connection(tenant, channel);
    return new ChannelView(
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

  @Transactional
  public ChannelView configureChannel(
      UUID tenant, NotificationChannel channel, ConfigurationChange change) {
    version(change.version());
    var current = repository.connection(tenant, channel);
    if (current.version() != change.version())
      throw new ConflictException("Setting version has changed");
    var config = change.connectionConfig();
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
            change.enabled(),
            connectionRef,
            credentialsRef,
            null,
            config.get("chatId"),
            null,
            change.version(),
            config);
    if (change.enabled()
        && (!connections.available(channel) || !connections.ready(tenant, prepared))) {
      throw invalid("Channel provider is unavailable or connection is not verified");
    }
    repository.saveConnection(tenant, prepared, change.version());
    return channel(tenant, channel);
  }

  @Transactional
  public ChannelView changeChannel(UUID tenant, NotificationChannel channel, ChannelChange change) {
    version(change.version());
    var current = repository.connection(tenant, channel);
    if (current.version() != change.version())
      throw new ConflictException("Setting version has changed");
    if (change.messageThreadId() != null && change.messageThreadId() <= 0)
      throw invalid("Invalid message thread ID");
    if (change.chatId() != null && (change.chatId().isBlank() || change.chatId().length() > 100))
      throw invalid("Invalid chat ID");
    if (channel != NotificationChannel.TELEGRAM
        && (change.botToken() != null
            || change.chatId() != null
            || change.messageThreadId() != null))
      throw invalid("Telegram fields are only supported by TELEGRAM");
    Connection prepared;
    if (channel == NotificationChannel.IN_APP) {
      prepared =
          new Connection(
              channel, true, change.enabled(), null, null, null, null, null, current.version());
    } else if (change.botToken() == null
        && change.chatId() == null
        && change.messageThreadId() == null) {
      prepared =
          new Connection(
              channel,
              true,
              change.enabled(),
              current.connectionRef(),
              current.credentialsRef(),
              current.botUsername(),
              current.chatId(),
              current.messageThreadId(),
              current.version(),
              current.connectionConfig());
    } else {
      prepared = connections.prepare(tenant, current, change);
    }
    if (prepared.channel() != channel || prepared.enabled() != change.enabled())
      throw invalid("Invalid connection revision");
    if (change.enabled()
        && (!connections.available(channel) || !connections.ready(tenant, prepared)))
      throw invalid("Channel provider is unavailable or connection is not verified");
    repository.saveConnection(tenant, prepared, change.version());
    return channel(tenant, channel);
  }

  private static void version(long version) {
    if (version < 0 || version == Long.MAX_VALUE) throw invalid("Invalid version");
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
