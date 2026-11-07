package com.vishop.notification.modules.setting.application.api;

import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Value;

public interface NotificationAlertRuleApi {
  @Value
  class RuleView {
    UUID id;
    String type;
    String name;
    boolean enabled;
    Set<NotificationChannel> channels;
    long version;
  }

  @Value
  class RuleChange {
    boolean enabled;
    Set<NotificationChannel> channels;
    long version;

    public RuleChange(boolean enabled, Set<NotificationChannel> channels, long version) {
      this.enabled = enabled;
      this.channels = Set.copyOf(channels);
      this.version = version;
    }
  }

  List<RuleView> rules(UUID tenantId);

  RuleView ruleById(UUID tenantId, UUID ruleId);

  RuleView ruleForType(UUID tenantId, NotificationTypeCode type);

  RuleView createRule(
      UUID tenantId, NotificationTypeCode type, boolean enabled, Set<NotificationChannel> channels);

  RuleView updateRule(UUID tenantId, UUID ruleId, RuleChange change);
}
