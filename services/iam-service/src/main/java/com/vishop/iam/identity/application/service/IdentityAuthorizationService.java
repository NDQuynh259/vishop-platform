package com.vishop.iam.identity.application.service;

import com.vishop.iam.identity.domain.entities.User;
import com.vishop.iam.identity.domain.ports.UserRepositoryPort;
import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuthorizationService {
  private final UserRepositoryPort users;

  public IdentityAuthorizationService(UserRepositoryPort users) {
    this.users = users;
  }

  public User activeUser(UUID id) {
    User user = users.user(id).orElseThrow(() -> new UnauthorizedException("Unknown account"));
    user.requireActive();
    return user;
  }

  public void cms(IamActor actor) {
    if (activeUser(actor.userId()).systemRole() != SystemRole.PLATFORM_ADMIN)
      throw new ForbiddenException("CMS requires PLATFORM_ADMIN");
  }
}
