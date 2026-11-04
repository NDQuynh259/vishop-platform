package com.vishop.notification.modules.inbox.domain.port;

import com.vishop.notification.modules.inbox.domain.entity.Notification;
import com.vishop.notification.modules.inbox.domain.entity.NotificationRecipient;
import java.util.List;

/** Append within the caller's dispatch transaction; never expose managed entities. */
public interface NotificationInboxRepository {
  void append(Notification notification, List<NotificationRecipient> recipients);
}
