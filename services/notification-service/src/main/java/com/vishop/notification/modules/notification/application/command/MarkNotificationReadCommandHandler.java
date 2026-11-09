package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.dto.NotificationView;
import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarkNotificationReadCommandHandler
    implements CommandHandler<MarkNotificationReadCommand, NotificationView> {
  private final NotificationService service;

  @Override
  @Transactional
  public NotificationView handle(MarkNotificationReadCommand request) {
    return service.markRead(request.getActor(), request.getNotificationId());
  }
}
