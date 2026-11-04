package com.vishop.notification.modules.configuration.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.HashSet;
import java.util.Set;

public record UserPreference(boolean enabled, Set<NotificationChannel> channels) {
  public UserPreference {
    channels = Set.copyOf(channels);
  }

  public Set<NotificationChannel> restrict(Set<NotificationChannel> tenantChannels) {
    if (!enabled) return Set.of();
    var result = new HashSet<>(tenantChannels);
    result.retainAll(channels);
    return Set.copyOf(result);
  }
}
