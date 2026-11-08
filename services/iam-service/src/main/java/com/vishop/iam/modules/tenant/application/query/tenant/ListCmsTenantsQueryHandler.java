package com.vishop.iam.modules.tenant.application.query.tenant;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.port.TenantReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsTenantsQueryHandler
    implements QueryHandler<ListCmsTenantsQuery, PagedResult<TenantResponseDto>> {
  private final TenantReadPort tenants;
  private final IdentityApi authorization;

  public ListCmsTenantsQueryHandler(TenantReadPort tenants, IdentityApi authorization) {
    this.tenants = tenants;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<TenantResponseDto> handle(ListCmsTenantsQuery query) {
    var actor = query.actor();

    authorization.requireCms(actor);
    return tenants.findAll(query.pagination());
  }
}
