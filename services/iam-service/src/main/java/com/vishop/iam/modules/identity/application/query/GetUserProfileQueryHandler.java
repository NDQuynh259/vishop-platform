package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetUserProfileQueryHandler implements QueryHandler<GetUserProfileQuery, UserView> {
  private final UserRepositoryPort users;

  public GetUserProfileQueryHandler(UserRepositoryPort users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserView handle(GetUserProfileQuery query) {
    return UserViewMapper.from(
        users
            .user(query.actor().userId())
            .orElseThrow(() -> new EntityNotFoundException("User", query.actor().userId())));
  }
}
