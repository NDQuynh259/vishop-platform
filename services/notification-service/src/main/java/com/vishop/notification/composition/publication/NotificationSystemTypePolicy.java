package com.vishop.notification.composition.publication;

import com.vishop.notification.modules.announcement.application.port.NotificationAnnouncementTypePolicy;
import com.vishop.notification.shared.constants.NotificationTypeCode;

/** Validate CMS announcements against the shared supported-type constants. */
public final class NotificationSystemTypePolicy implements NotificationAnnouncementTypePolicy {
  @Override
  public void requirePublishableSystemType(String code) {
    var type =
        NotificationTypeCode.fromCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type"));
    if (type.category() != NotificationTypeCode.Category.SYSTEM)
      throw new IllegalArgumentException("SYSTEM notification type required");
  }
}
