package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IdentityApplicationApi implements IdentityApi {
  private final UserRepositoryPort users;

  public IdentityApplicationApi(UserRepositoryPort users) {
    this.users = users;
  }

  public UserView requireActive(UUID userId) {
    var user =
        users
            .user(userId)
            .orElseThrow(
                () ->
                    new io.github.coreplatform.common.domain.exception.UnauthorizedException(
                        "Unknown account"));
    user.requireActive();
    return UserViewMapper.from(user);
  }

  public void requireCms(AuthenticatedUser actor) {
    actor.requireCms();
  }

  public boolean isActive(UUID userId) {
    return users.user(userId).map(user -> "ACTIVE".equals(user.status())).orElse(false);
  }
}
