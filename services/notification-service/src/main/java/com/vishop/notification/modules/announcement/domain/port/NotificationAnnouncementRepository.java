package com.vishop.notification.modules.announcement.domain.port;

import com.vishop.notification.modules.announcement.domain.entity.NotificationAnnouncement;
import java.util.Optional;
import java.util.UUID;

public interface NotificationAnnouncementRepository {
  Optional<NotificationAnnouncement> find(UUID id);

  /** expectedVersion=-1 means insert; otherwise compare-and-swap, never blind overwrite. */
  void save(NotificationAnnouncement announcement, long expectedVersion);
}
