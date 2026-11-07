package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.ChannelView;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class CreateChannelCommand implements Command<ChannelView> {
  UUID tenantId;
  NotificationChannel channel;
  String name;
  boolean enabled;
  Map<String, String> connectionConfig;

  public CreateChannelCommand(
      UUID tenantId,
      NotificationChannel channel,
      String name,
      boolean enabled,
      Map<String, String> connectionConfig) {
    this.tenantId = tenantId;
    this.channel = channel;
    this.name = name;
    this.enabled = enabled;
    this.connectionConfig = Map.copyOf(connectionConfig);
  }
}
