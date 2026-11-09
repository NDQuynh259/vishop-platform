package com.vishop.notification.modules.notification.application.api;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.InboxFilter;
import com.vishop.notification.modules.notification.application.dto.NotificationView;
import com.vishop.notification.modules.notification.application.dto.UnreadCount;
import com.vishop.notification.modules.notification.application.dto.UpdatedCount;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface NotificationApi {
  UUID create(NotificationMessage message, Set<UUID> inAppRecipients);

  PagedResult<NotificationView> list(InboxActor actor, InboxFilter filter);

  NotificationView getById(InboxActor actor, UUID notificationId);

  UnreadCount unreadCount(InboxActor actor);

  NotificationView markRead(InboxActor actor, UUID notificationId);

  UpdatedCount markAllRead(InboxActor actor, Instant before);

  void archive(InboxActor actor, UUID notificationId);
}
