package com.vishop.iam.modules.access.application.query;

import com.vishop.iam.modules.access.application.dto.MemberView;
import com.vishop.iam.modules.access.application.port.AccessReadPort;
import com.vishop.iam.modules.access.application.service.AccessAuthorizationService;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTenantMembersQueryHandler
    implements QueryHandler<ListTenantMembersQuery, List<MemberView>> {
  private final AccessReadPort iamRead;
  private final AccessAuthorizationService authorization;

  public ListTenantMembersQueryHandler(
      AccessReadPort iamRead, AccessAuthorizationService authorization) {
    this.iamRead = iamRead;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MemberView> handle(ListTenantMembersQuery query) {
    var actor = query.actor();
    var tenant = query.tenant();

    authorization.access(actor.userId(), tenant, CommonPermissions.Tenant.MEMBER_VIEW);
    return iamRead.members(tenant);
  }
}
