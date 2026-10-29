package com.vishop.iam.modules.tenant.application.query.tenant;

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
  private final TenantReadPort tenants;

  public ListMyTenantsQueryHandler(TenantReadPort tenants) {
    this.tenants = tenants;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PagedResult<TenantResponseDto> handle(ListMyTenantsQuery query) {
    if (query.cursorPagination() != null) {
      return tenants.findByUserIdAfter(
          query.actor().userId(), query.cursorPagination(), query.criteria());
    }
    return tenants.findByUserId(query.actor().userId(), query.pagination(), query.criteria());
  }
}
