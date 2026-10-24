package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.contracts.identity.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
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
    return UserViewMapper.from(authorization.activeUser(query.actor().userId()));
  }
}
