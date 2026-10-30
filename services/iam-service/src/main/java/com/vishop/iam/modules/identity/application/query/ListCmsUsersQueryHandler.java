package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.identity.application.port.UserReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsUsersQueryHandler implements QueryHandler<ListCmsUsersQuery, List<UserView>> {
  private final UserReadPort iamRead;

  public ListCmsUsersQueryHandler(UserReadPort iamRead) {
    this.iamRead = iamRead;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserView> handle(ListCmsUsersQuery query) {
    var actor = query.actor();
    var limit = query.limit();
    var offset = query.offset();

    actor.requireCms();
    return iamRead.users(limit, offset);
  }
}
