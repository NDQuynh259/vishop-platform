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
  private final com.vishop.iam.modules.identity.application.service.SessionAuthenticationService
      authentication;

  public IdentityApplicationApi(
      UserRepositoryPort users,
      com.vishop.iam.modules.identity.application.service.SessionAuthenticationService
          authentication) {
    this.users = users;
    this.authentication = authentication;
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AuthenticatedUser resolveContext(AuthenticatedUser context, UUID roleId) {
    return authentication.resolveContext(context, roleId);
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AuthenticatedUser verifyAccessToken(String token) {
    return authentication.verifyAccessToken(token);
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
