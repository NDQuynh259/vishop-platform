package com.vishop.notification.modules.announcement.application.api;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface NotificationAnnouncementApi {
  enum AudienceMode {
    ALL_ACTIVE_TENANTS,
    SELECTED_TENANTS
  }

  record Audience(AudienceMode mode, Set<UUID> tenantIds) {
    public Audience {
      tenantIds = Set.copyOf(tenantIds);
    }
  }

  record Draft(
      String type,
      String title,
      String body,
      Audience audience,
      Set<NotificationChannel> channels) {
    public Draft {
      channels = Set.copyOf(channels);
    }
  }

  record Snapshot(
      UUID id,
      String type,
      String title,
      String body,
      Audience audience,
      Set<NotificationChannel> channels,
      long version,
      Instant publishedAt) {
    public Snapshot {
      channels = Set.copyOf(channels);
    }
  }

  Snapshot create(Draft draft);

  Snapshot revise(UUID id, long expectedVersion, Draft draft);

  Snapshot publish(UUID id, long expectedVersion);
}
