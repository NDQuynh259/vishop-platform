package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.dto.InboxActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;
import lombok.Value;

@Value
public class ArchiveNotificationCommand implements Command<Void> {
  InboxActor actor;
  UUID notificationId;
}
