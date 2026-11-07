package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.service.NotificationInboxApplicationService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateNotificationCommandHandler
    implements CommandHandler<CreateNotificationCommand, UUID> {
  private final NotificationInboxApplicationService service;

  @Override
  @Transactional
  public UUID handle(CreateNotificationCommand request) {
    return service.create(request.getMessage(), request.getRecipients());
  }
}
