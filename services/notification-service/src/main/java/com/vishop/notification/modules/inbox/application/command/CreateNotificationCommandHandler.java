package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.modules.inbox.application.api.NotificationInboxApi;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateNotificationCommandHandler
    implements CommandHandler<CreateNotificationCommand, UUID> {
  private final NotificationInboxApi application;

  @Override
  @Transactional
  public UUID handle(CreateNotificationCommand request) {
    return application.create(request.getMessage(), request.getRecipients());
  }
}
