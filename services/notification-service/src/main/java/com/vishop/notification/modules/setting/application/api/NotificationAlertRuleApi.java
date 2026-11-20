package com.vishop.notification.modules.setting.application.api;

import com.vishop.notification.shared.constants.NotificationFeatureCode;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import java.util.List;
import java.util.UUID;
import lombok.Value;

public interface NotificationAlertRuleApi {
  @Value
  class RuleView {
    UUID id;
    String type;
    String name;
    boolean enabled;
    NotificationFeatureCode featureCode;
    long version;

    public String getFeatureName() {
      return getFeatureCode().displayName();
    }
  }

  @Value
  class RuleChange {
    boolean enabled;
    NotificationFeatureCode featureCode;
    long version;
  }

  List<RuleView> rules(UUID tenantId);

  RuleView ruleById(UUID tenantId, UUID ruleId);

  RuleView ruleForType(UUID tenantId, NotificationTypeCode type);

  RuleView createRule(
      UUID tenantId, NotificationTypeCode type, boolean enabled, NotificationFeatureCode featureCode);

  RuleView updateRule(UUID tenantId, UUID ruleId, RuleChange change);
}
