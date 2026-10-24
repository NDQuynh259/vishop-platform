package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.contracts.identity.UserView;
import com.vishop.iam.modules.identity.application.port.UserReadPort;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsUsersQueryHandler implements QueryHandler<ListCmsUsersQuery, List<UserView>> {
  private final UserReadPort iamRead;
  private final IdentityAuthorizationService authorization;

  public ListCmsUsersQueryHandler(
      UserReadPort iamRead, IdentityAuthorizationService authorization) {
    this.iamRead = iamRead;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserView> handle(ListCmsUsersQuery query) {
    var actor = query.actor();
    var limit = query.limit();
    var offset = query.offset();

    authorization.cms(actor);
    return iamRead.users(limit, offset);
  }
}
