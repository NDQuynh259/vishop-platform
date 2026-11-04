package com.vishop.notification.modules.configuration.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Live configuration projection; missing rows must be represented using the documented defaults.
 */
public record TenantConfiguration(
    boolean featureEnabled, Set<NotificationChannel> enabledChannels, Optional<TypeRule> typeRule) {
  public record TypeRule(
      boolean enabled, Set<NotificationChannel> channels, Set<UUID> recipientIds) {
    public TypeRule {
      channels = Set.copyOf(channels);
      recipientIds = Set.copyOf(recipientIds);
    }
  }

  public TenantConfiguration {
    enabledChannels = Set.copyOf(enabledChannels);
    typeRule = java.util.Objects.requireNonNull(typeRule);
  }

  public static TenantConfiguration defaults() {
    return new TenantConfiguration(true, Set.of(NotificationChannel.IN_APP), Optional.empty());
  }

  public Set<NotificationChannel> resolve(
      boolean defaultEnabled, Set<NotificationChannel> defaults) {
    if (!featureEnabled || !typeRule.map(TypeRule::enabled).orElse(defaultEnabled)) return Set.of();
    var result = new HashSet<>(typeRule.map(TypeRule::channels).orElse(defaults));
    result.retainAll(enabledChannels);
    return Set.copyOf(result);
  }
}
