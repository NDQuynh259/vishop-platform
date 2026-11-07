package com.vishop.notification.modules.setting.application.command;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleChange;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;
import lombok.Value;

@Value
public class UpdateRuleCommand implements Command<RuleView> {
  UUID tenantId;
  UUID ruleId;
  RuleChange change;
}
