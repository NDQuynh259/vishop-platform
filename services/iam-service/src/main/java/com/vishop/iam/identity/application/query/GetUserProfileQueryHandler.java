package com.vishop.iam.identity.application.query;

import com.vishop.iam.identity.application.dto.UserView;
import com.vishop.iam.identity.application.service.IdentityAuthorizationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetUserProfileQueryHandler implements QueryHandler<GetUserProfileQuery, UserView> {
  private final IdentityAuthorizationService authorization;

  public GetUserProfileQueryHandler(IdentityAuthorizationService authorization) {
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public UserView handle(GetUserProfileQuery query) {
    return UserView.from(authorization.activeUser(query.actor().userId()));
  }
}
