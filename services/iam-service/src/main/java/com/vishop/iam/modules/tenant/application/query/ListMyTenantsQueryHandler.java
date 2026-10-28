package com.vishop.iam.modules.tenant.application.query;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.port.TenantReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListMyTenantsQueryHandler
    implements QueryHandler<ListMyTenantsQuery, PagedResult<TenantResponseDto>> {
  private final IdentityApi identities;
  private final AccessApi access;
  private final TenantReadPort tenants;

  public ListMyTenantsQueryHandler(
      IdentityApi identities, AccessApi access, TenantReadPort tenants) {
    this.identities = identities;
    this.access = access;
    this.tenants = tenants;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PagedResult<TenantResponseDto> handle(ListMyTenantsQuery query) {
    identities.requireActive(query.actor().userId());
    return tenants.findByTenantIds(
        access.activeTenantIds(query.actor().userId()), query.pagination(), query.criteria());
  }
}
