package com.vishop.notification.modules.notification.domain.port;

import com.vishop.notification.modules.notification.domain.entity.Notification;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxEntry;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxFilter;
import com.vishop.notification.modules.notification.domain.entity.NotificationInboxScope;
import com.vishop.notification.modules.notification.domain.entity.NotificationRecipient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
  UUID append(Notification notification, List<NotificationRecipient> recipients);

  List<NotificationInboxEntry> findAll(
      NotificationInboxScope scope, NotificationInboxFilter filter);

  long count(NotificationInboxScope scope, NotificationInboxFilter filter);

  Optional<NotificationInboxEntry> findById(NotificationInboxScope scope, UUID notificationId);

  long unreadCount(NotificationInboxScope scope);

  NotificationInboxEntry markRead(NotificationInboxScope scope, UUID notificationId, Instant at);

  int markAllRead(NotificationInboxScope scope, Instant before, Instant at);

  void archive(NotificationInboxScope scope, UUID notificationId, Instant at);
}
