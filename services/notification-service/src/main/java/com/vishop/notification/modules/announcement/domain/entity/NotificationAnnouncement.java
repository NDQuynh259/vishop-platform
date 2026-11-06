package com.vishop.notification.modules.announcement.domain.entity;

import com.vishop.notification.shared.contract.NotificationChannel;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** CMS draft/published snapshot. Durable storage and audience fan-out require future migrations. */
public record NotificationAnnouncement(
    UUID id,
    String type,
    String title,
    String body,
    Audience audience,
    Set<NotificationChannel> channels,
    long version,
    Instant publishedAt) {
  public enum AudienceMode {
    ALL_ACTIVE_TENANTS,
    SELECTED_TENANTS
  }

  public record Audience(AudienceMode mode, Set<UUID> tenantIds) {
    public Audience {
      Objects.requireNonNull(mode);
      tenantIds = Set.copyOf(tenantIds);
      if ((mode == AudienceMode.SELECTED_TENANTS) == tenantIds.isEmpty())
        throw new IllegalArgumentException("Explicit audience required");
    }
  }

  public NotificationAnnouncement {
    Objects.requireNonNull(id);
    Objects.requireNonNull(audience);
    channels = Set.copyOf(channels);
    if (type == null
        || type.isBlank()
        || title == null
        || title.isBlank()
        || body == null
        || channels.isEmpty()
        || version < 0) throw new IllegalArgumentException("Invalid announcement");
  }

  public NotificationAnnouncement revise(
      String title, String body, Audience audience, Set<NotificationChannel> channels) {
    requireDraft();
    return new NotificationAnnouncement(
        id, type, title, body, audience, channels, version + 1, null);
  }

  public NotificationAnnouncement publish(Instant at) {
    requireDraft();
    return new NotificationAnnouncement(
        id, type, title, body, audience, channels, version + 1, Objects.requireNonNull(at));
  }

  private void requireDraft() {
    if (publishedAt != null) throw new IllegalStateException("Published announcement is immutable");
  }
}
