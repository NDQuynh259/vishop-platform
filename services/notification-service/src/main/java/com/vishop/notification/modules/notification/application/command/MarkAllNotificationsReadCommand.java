package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.dto.UpdatedCount;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.time.Instant;
import lombok.Value;

@Value
public class MarkAllNotificationsReadCommand implements Command<UpdatedCount> {
  InboxActor actor;
  Instant before;
}
