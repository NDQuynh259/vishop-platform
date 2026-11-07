package com.vishop.notification.modules.inbox.application.command;

import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class CreateNotificationCommand implements Command<UUID> {
  NotificationMessage message;
  Set<UUID> recipients;

  public CreateNotificationCommand(NotificationMessage message, Set<UUID> recipients) {
    this.message = Objects.requireNonNull(message);
    this.recipients = Set.copyOf(recipients);
  }
}
