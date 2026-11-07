package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.dto.NotificationView;
import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarkNotificationReadCommandHandler
    implements CommandHandler<MarkNotificationReadCommand, NotificationView> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional
  public NotificationView handle(MarkNotificationReadCommand request) {
    return service.markRead(request.getActor(), request.getNotificationId());
  }
}
