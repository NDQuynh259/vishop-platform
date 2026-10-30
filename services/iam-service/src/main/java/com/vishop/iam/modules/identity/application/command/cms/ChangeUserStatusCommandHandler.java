package com.vishop.iam.modules.identity.application.command.cms;

import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.constants.SystemRole;
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
  private final IamEventPublisherPort outbox;

  public ChangeUserStatusCommandHandler(
      UserRepositoryPort userRepository, IamEventPublisherPort outbox) {
    this.userRepository = userRepository;
    this.outbox = outbox;
  }

  @Override
  @Transactional
  public Void handle(ChangeUserStatusCommand command) {
    var actor = command.actor();
    var user = command.user();
    var status = command.status();

    userRepository.lockSystemRoles();
    actor.requireCms();
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
    outbox.append(
        "UserStatusChanged",
        "User",
        user,
        null,
        Map.of("actorId", actor.userId(), "status", status));

    return null;
  }
}
