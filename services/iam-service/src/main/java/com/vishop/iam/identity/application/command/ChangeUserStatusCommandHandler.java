package com.vishop.iam.identity.application.command;

import com.vishop.iam.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.identity.domain.entities.User;
import com.vishop.iam.identity.domain.ports.SessionRepositoryPort;
import com.vishop.iam.identity.domain.ports.UserRepositoryPort;
import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChangeUserStatusCommandHandler
    implements CommandHandler<ChangeUserStatusCommand, Void> {
  private final UserRepositoryPort userRepository;
  private final SessionRepositoryPort sessionRepository;
  private final IamEventPublisherPort outbox;
  private final IdentityAuthorizationService authorization;

  public ChangeUserStatusCommandHandler(
      UserRepositoryPort userRepository,
      SessionRepositoryPort sessionRepository,
      IamEventPublisherPort outbox,
      IdentityAuthorizationService authorization) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.outbox = outbox;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public Void handle(ChangeUserStatusCommand command) {
    var actor = command.actor();
    var user = command.user();
    var status = command.status();

    userRepository.lockSystemRoles();
    authorization.cms(actor);
    if (!Set.of("ACTIVE", "SUSPENDED", "INACTIVE").contains(status))
      throw new BadRequestException("Invalid user status");
    User target =
        userRepository.user(user).orElseThrow(() -> new EntityNotFoundException("User", user));
    if (target.systemRole() == SystemRole.PLATFORM_ADMIN
        && "ACTIVE".equals(target.status())
        && !"ACTIVE".equals(status)
        && userRepository.activeAdmins() <= 1)
      throw new ConflictException("The final active platform administrator cannot be suspended");
    userRepository.updateUserStatus(user, status);
    if (!"ACTIVE".equals(status)) sessionRepository.revokeAllSessions(user);
    outbox.append(
        "UserStatusChanged",
        "User",
        user,
        null,
        Map.of("actorId", actor.userId(), "status", status));

    return null;
  }
}
