package com.vishop.notification.modules.setting.application.query;

import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleView;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;
import lombok.Value;

@Value
public class GetRuleQuery implements Query<RuleView> {
  UUID tenantId;
  UUID ruleId;
}
