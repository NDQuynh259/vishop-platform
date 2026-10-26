package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IdentityApplicationApi implements IdentityApi {
  private final IdentityAuthorizationService authorization;
  private final UserRepositoryPort users;

  public IdentityApplicationApi(
      IdentityAuthorizationService authorization, UserRepositoryPort users) {
    this.authorization = authorization;
    this.users = users;
  }

  public UserView requireActive(UUID userId) {
    return UserViewMapper.from(authorization.activeUser(userId));
  }

  public void requireCms(AuthenticatedUser actor) {
    authorization.cms(actor);
  }

  public boolean isActive(UUID userId) {
    return users.user(userId).map(user -> "ACTIVE".equals(user.status())).orElse(false);
  }
}
