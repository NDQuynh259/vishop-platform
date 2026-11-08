package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.port.UserReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsUsersQueryHandler
    implements QueryHandler<ListCmsUsersQuery, PagedResult<UserView>> {
  private final UserReadPort iamRead;

  public ListCmsUsersQueryHandler(UserReadPort iamRead) {
    this.iamRead = iamRead;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<UserView> handle(ListCmsUsersQuery query) {
    var actor = query.actor();

    actor.requireCms();
    return iamRead.users(query.pagination());
  }
}
