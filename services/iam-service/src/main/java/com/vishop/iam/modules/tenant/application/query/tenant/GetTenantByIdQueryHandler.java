package com.vishop.iam.modules.tenant.application.query.tenant;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetTenantByIdQueryHandler
    implements QueryHandler<GetTenantByIdQuery, TenantResponseDto> {
  private final TenantApi tenants;
  private final TenantApi authorization;

  public GetTenantByIdQueryHandler(TenantApi tenants, TenantApi authorization) {
    this.tenants = tenants;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public TenantResponseDto handle(GetTenantByIdQuery query) {
    var actor = query.actor();
    var id = query.id();

    authorization.requireAccess(actor, id, CommonPermissions.Tenant.VIEW);
    return tenants.view(id);
  }
}
