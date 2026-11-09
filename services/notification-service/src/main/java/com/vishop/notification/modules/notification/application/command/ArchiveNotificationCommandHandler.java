package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ArchiveNotificationCommandHandler
    implements CommandHandler<ArchiveNotificationCommand, Void> {
  private final NotificationService service;

  @Override
  @Transactional
  public Void handle(ArchiveNotificationCommand request) {
    service.archive(request.getActor(), request.getNotificationId());
    return null;
  }
}
