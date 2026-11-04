package com.vishop.notification.modules.configuration.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Set;
import java.util.UUID;

public interface ConfigurationApi {
  record TypePolicy(
      String code,
      String featureCode,
      boolean available,
      boolean defaultEnabled,
      Set<NotificationChannel> defaultChannels) {
    public TypePolicy {
      defaultChannels = Set.copyOf(defaultChannels);
    }
  }

  record RoutingPolicy(Set<NotificationChannel> channels, Set<UUID> selectedRecipients) {
    public RoutingPolicy {
      channels = Set.copyOf(channels);
      selectedRecipients = Set.copyOf(selectedRecipients);
    }
  }

  RoutingPolicy resolve(UUID tenantId, TypePolicy type);

  Set<NotificationChannel> restrictForUser(
      UUID tenantId, UUID userId, String type, Set<NotificationChannel> tenantChannels);
}
