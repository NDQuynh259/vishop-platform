package com.vishop.iam.modules.identity.application.command.logout;

import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LogoutCommandHandler implements CommandHandler<LogoutCommand, Void> {
  private final IamEventPublisherPort outbox;

  public LogoutCommandHandler(IamEventPublisherPort outbox) {
    this.outbox = outbox;
  }

  @Override
  @Transactional
  public Void handle(LogoutCommand command) {
    var actor = command.actor();

    outbox.append("UserLoggedOut", "User", actor.userId(), null, Map.of("userId", actor.userId()));

    return null;
  }
}
