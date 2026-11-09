package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.dto.UpdatedCount;
import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarkAllNotificationsReadCommandHandler
    implements CommandHandler<MarkAllNotificationsReadCommand, UpdatedCount> {
  private final NotificationService service;

  @Override
  @Transactional
  public UpdatedCount handle(MarkAllNotificationsReadCommand request) {
    return service.markAllRead(request.getActor(), request.getBefore());
  }
}
