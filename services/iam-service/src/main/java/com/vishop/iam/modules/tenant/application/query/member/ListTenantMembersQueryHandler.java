package com.vishop.iam.modules.tenant.application.query.member;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi.MemberReadPort;
import com.vishop.iam.modules.tenant.application.dto.MemberView;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTenantMembersQueryHandler
    implements QueryHandler<ListTenantMembersQuery, List<MemberView>> {
  private final MemberReadPort iamRead;
  private final TenantApi authorization;

  public ListTenantMembersQueryHandler(MemberReadPort iamRead, TenantApi authorization) {
    this.iamRead = iamRead;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MemberView> handle(ListTenantMembersQuery query) {
    var actor = query.actor();
    var tenant = query.tenant();

    authorization.requireAccess(actor, tenant, CommonPermissions.Tenant.MEMBER_VIEW);
    return iamRead.members(tenant);
  }
}
