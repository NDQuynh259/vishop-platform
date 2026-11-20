package com.vishop.notification.composition.dispatch;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.util.Set;
import java.util.UUID;

/** Routing policy port; the current adapter combines channel settings and per-type rules. */
public interface NotificationDispatchPolicy {
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

  record GroupSetting(NotificationChannel channel, UUID settingId) {}

  record RoutingPolicy(
      Set<NotificationChannel> channels, Set<UUID> selectedRecipients, Set<GroupSetting> groups) {
    public RoutingPolicy(Set<NotificationChannel> channels, Set<UUID> selectedRecipients) {
      this(channels, selectedRecipients, Set.of());
    }

    public RoutingPolicy {
      channels = Set.copyOf(channels);
      selectedRecipients = Set.copyOf(selectedRecipients);
      groups = Set.copyOf(groups);
    }
  }

  RoutingPolicy resolve(UUID tenantId, TypePolicy type);

  Set<NotificationChannel> restrictForUser(
      UUID tenantId, UUID userId, String type, Set<NotificationChannel> tenantChannels);
}
