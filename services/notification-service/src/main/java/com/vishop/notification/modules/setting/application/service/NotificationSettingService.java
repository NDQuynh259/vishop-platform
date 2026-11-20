package com.vishop.notification.modules.setting.application.service;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.port.NotificationSettingConnectionPolicy;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationSettingService implements NotificationSettingManagementApi {
  private final NotificationSettingManagementRepository repository;
  private final NotificationSettingConnectionPolicy connections;
  private final NotificationChannelSupport support;

  public List<ChannelView> channels(UUID tenant) {
    return Arrays.stream(NotificationChannel.values())
        .flatMap(c -> repository.connections(tenant, c).stream())
        .map(c -> support.view(tenant, c))
        .toList();
  }

  public ChannelView channel(UUID tenant, NotificationChannel channel) {
    var c = repository.connection(tenant, channel);
    return support.view(tenant, c);
  }

  public ChannelView channelById(UUID tenant, UUID channelId) {
    return support.view(tenant, repository.connectionById(tenant, channelId));
  }

  @Transactional
  public ChannelView createChannel(
      UUID tenant,
      NotificationChannel channel,
      String name,
      boolean enabled,
      Map<String, String> config) {
    return createChannel(tenant, channel, name, enabled, config, Set.of());
  }

  @Transactional
  public ChannelView createChannel(
      UUID tenant,
      NotificationChannel channel,
      String name,
      boolean enabled,
      Map<String, String> config,
      Set<NotificationTypeCode> allowedTypes) {
    validateTypes(channel, allowedTypes);
    var prepared =
        support
            .configuredConnection(tenant, channel, enabled, config, 0)
            .withSettings(support.settingName(name, channel.name()), allowedTypes);
    var saved = repository.createConnection(tenant, prepared);
    return support.view(tenant, saved);
  }

  @Transactional
  public ChannelView updateChannel(UUID tenant, UUID channelId, ChannelUpdate change) {
    var current = repository.connectionById(tenant, channelId);
    var channel = current.channel();
    version(change.getVersion());
    if (current.version() != change.getVersion())
      throw new ConflictException("Setting version has changed");
    boolean legacyFields =
        change.getBotToken() != null
            || change.getChatId() != null
            || change.getMessageThreadId() != null;
    if (change.getConnectionConfig() != null && legacyFields)
      throw invalid("Use connectionConfig without legacy Telegram fields");
    if (legacyFields && channel != NotificationChannel.TELEGRAM)
      throw invalid("Telegram fields are only supported by TELEGRAM");
    if (change.getMessageThreadId() != null && change.getMessageThreadId() <= 0)
      throw invalid("Invalid message thread ID");
    if (change.getChatId() != null
        && (change.getChatId().isBlank() || change.getChatId().length() > 100))
      throw invalid("Invalid chat ID");

    Connection prepared;
    if (change.getConnectionConfig() != null) {
      prepared =
          support.configuredConnection(
              tenant,
              channel,
              change.isEnabled(),
              change.getConnectionConfig(),
              change.getVersion());
    } else if (legacyFields) {
      prepared = connections.prepare(tenant, current, change);
    } else {
      prepared =
          new Connection(
              channel,
              true,
              change.isEnabled(),
              current.connectionRef(),
              current.credentialsRef(),
              current.botUsername(),
              current.chatId(),
              current.messageThreadId(),
              current.version(),
              current.connectionConfig());
    }
    if (prepared.channel() != channel || prepared.enabled() != change.isEnabled())
      throw invalid("Invalid connection revision");
    if (change.getConnectionConfig() == null
        && change.isEnabled()
        && (!connections.available(channel) || !connections.ready(tenant, prepared)))
      throw invalid("Channel provider is unavailable or connection is not verified");
    var types =
        change.getAllowedTypes() == null ? current.allowedTypes() : change.getAllowedTypes();
    validateTypes(channel, types);
    prepared = prepared.withSettings(support.settingName(change.getName(), current.name()), types);
    var saved = repository.saveConnectionById(tenant, channelId, prepared, change.getVersion());
    return support.view(tenant, saved);
  }

  public ChannelRulesView channelRules(UUID tenant, UUID settingId) {
    var selection = repository.channelRules(tenant, settingId);
    return new ChannelRulesView(selection.settingId(), selection.version(), selection.ruleIds());
  }

  @Transactional
  public ChannelRulesView replaceChannelRules(
      UUID tenant, UUID settingId, long version, Set<UUID> ruleIds) {
    version(version);
    var selection = repository.replaceChannelRules(tenant, settingId, version, Set.copyOf(ruleIds));
    return new ChannelRulesView(selection.settingId(), selection.version(), selection.ruleIds());
  }

  private static void validateTypes(NotificationChannel channel, Set<NotificationTypeCode> types) {
    if (!channel.isGroupChannel() && !types.isEmpty())
      throw invalid("allowedTypes is supported only for Telegram and Lark groups");
  }

  private static void version(long version) {
    if (version < 0 || version == Long.MAX_VALUE) throw invalid("Invalid version");
  }

  private static BusinessRuleViolationException invalid(String message) {
    return new BusinessRuleViolationException(message);
  }
}
