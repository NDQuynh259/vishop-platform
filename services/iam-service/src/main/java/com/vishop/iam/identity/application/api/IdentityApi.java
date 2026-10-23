package com.vishop.iam.identity.application.api;

import com.vishop.iam.identity.application.dto.UserView;
import com.vishop.iam.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.identity.domain.ports.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.IamActor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityApi {
  private final IdentityAuthorizationService authorization;
  private final UserRepositoryPort users;

  public IdentityApi(IdentityAuthorizationService authorization, UserRepositoryPort users) {
    this.authorization = authorization;
    this.users = users;
  }

  public UserView requireActive(UUID userId) {
    return UserView.from(authorization.activeUser(userId));
  }

  public void requireCms(IamActor actor) {
    authorization.cms(actor);
  }

  public boolean isActive(UUID userId) {
    return users.user(userId).map(user -> "ACTIVE".equals(user.status())).orElse(false);
  }
}
