package com.vishop.notification.modules.notification.application.command;

import com.vishop.notification.modules.notification.application.service.NotificationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateNotificationCommandHandler
    implements CommandHandler<CreateNotificationCommand, UUID> {
  private final NotificationService service;

  @Override
  @Transactional
  public UUID handle(CreateNotificationCommand request) {
    return service.create(request.getMessage(), request.getRecipients());
  }
}
