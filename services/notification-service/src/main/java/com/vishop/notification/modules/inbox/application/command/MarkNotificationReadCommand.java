package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.dto.InboxActor;
import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;
import lombok.Value;

@Value
public class MarkNotificationReadCommand implements Command<NotificationView> {
  InboxActor actor;
  UUID notificationId;
}
