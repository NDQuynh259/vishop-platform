package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.dto.UpdatedCount;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarkAllNotificationsReadCommandHandler
    implements CommandHandler<MarkAllNotificationsReadCommand, UpdatedCount> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional
  public UpdatedCount handle(MarkAllNotificationsReadCommand request) {
    return service.markAllRead(request.getActor(), request.getBefore());
  }
}
