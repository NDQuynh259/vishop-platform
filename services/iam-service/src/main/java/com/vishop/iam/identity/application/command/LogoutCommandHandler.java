package com.vishop.iam.identity.application.command;

import com.vishop.iam.identity.domain.ports.SessionRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LogoutCommandHandler implements CommandHandler<LogoutCommand, Void> {
  private final SessionRepositoryPort sessionRepository;
  private final IamEventPublisherPort outbox;

  public LogoutCommandHandler(
      SessionRepositoryPort sessionRepository, IamEventPublisherPort outbox) {
    this.sessionRepository = sessionRepository;
    this.outbox = outbox;
  }

  @Override
  @Transactional
  public Void handle(LogoutCommand command) {
    var actor = command.actor();

    sessionRepository.revokeSession(actor.sessionId(), actor.userId());
    outbox.append("UserLoggedOut", "User", actor.userId(), null, Map.of("userId", actor.userId()));

    return null;
  }
}
