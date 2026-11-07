package com.vishop.notification.modules.inbox.application.query;

import com.vishop.notification.modules.inbox.application.dto.InboxActor;
import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;
import lombok.Value;

@Value
public class GetNotificationQuery implements Query<NotificationView> {
  InboxActor actor;
  UUID notificationId;
}
