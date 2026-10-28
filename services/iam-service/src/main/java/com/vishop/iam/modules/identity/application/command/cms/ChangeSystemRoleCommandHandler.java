package com.vishop.iam.modules.identity.application.command.cms;

import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChangeSystemRoleCommandHandler
    implements CommandHandler<ChangeSystemRoleCommand, Void> {
  private final UserRepositoryPort userRepository;
  private final IamEventPublisherPort outbox;
  private final IdentityAuthorizationService authorization;

  public ChangeSystemRoleCommandHandler(
      UserRepositoryPort userRepository,
      IamEventPublisherPort outbox,
      IdentityAuthorizationService authorization) {
    this.userRepository = userRepository;
    this.outbox = outbox;
    this.authorization = authorization;
  }

  @Override
  @Transactional
  public Void handle(ChangeSystemRoleCommand command) {
    var actor = command.actor();
    var user = command.user();
    var role = command.role();

    userRepository.lockSystemRoles();
    authorization.cms(actor);
    User target =
        userRepository.user(user).orElseThrow(() -> new EntityNotFoundException("User", user));
    if (target.systemRole() == SystemRole.PLATFORM_ADMIN
        && role != SystemRole.PLATFORM_ADMIN
        && "ACTIVE".equals(target.status())
        && userRepository.activeAdmins() <= 1)
      throw new ConflictException("The final active platform administrator cannot be demoted");
    userRepository.updateSystemRole(user, role);
    outbox.append(
        "SystemRoleChanged",
        "User",
        user,
        null,
        Map.of("actorId", actor.userId(), "systemRole", role));

    return null;
  }
}
