package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Set;
import java.util.UUID;
import lombok.Value;

@Value
public class CreateRuleCommand implements Command<RuleView> {
  UUID tenantId;
  NotificationTypeCode type;
  boolean enabled;
  Set<NotificationChannel> channels;

  public CreateRuleCommand(
      UUID tenantId,
      NotificationTypeCode type,
      boolean enabled,
      Set<NotificationChannel> channels) {
    this.tenantId = tenantId;
    this.type = type;
    this.enabled = enabled;
    this.channels = Set.copyOf(channels);
  }
}
