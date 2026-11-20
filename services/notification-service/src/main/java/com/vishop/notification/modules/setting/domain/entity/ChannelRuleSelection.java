package com.vishop.notification.modules.setting.domain.entity;

import java.util.Set;
import java.util.UUID;

public record ChannelRuleSelection(UUID settingId, long version, Set<UUID> ruleIds) {
  public ChannelRuleSelection {
    ruleIds = Set.copyOf(ruleIds);
  }
}
