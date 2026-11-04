package com.vishop.notification.modules.announcement.domain.port;

import com.vishop.notification.modules.announcement.domain.entity.Announcement;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository {
  Optional<Announcement> find(UUID id);

  /** expectedVersion=-1 means insert; otherwise compare-and-swap, never blind overwrite. */
  void save(Announcement announcement, long expectedVersion);
}
