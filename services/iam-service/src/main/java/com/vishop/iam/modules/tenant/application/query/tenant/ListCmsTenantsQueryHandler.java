package com.vishop.iam.modules.tenant.application.query.tenant;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsTenantsQueryHandler
    implements QueryHandler<ListCmsTenantsQuery, List<TenantResponseDto>> {
  private final TenantApi tenants;
  private final IdentityApi authorization;

  public ListCmsTenantsQueryHandler(TenantApi tenants, IdentityApi authorization) {
    this.tenants = tenants;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantResponseDto> handle(ListCmsTenantsQuery query) {
    var actor = query.actor();
    var limit = query.limit();
    var offset = query.offset();

    authorization.requireCms(actor);
    return tenants.tenants(limit, offset);
  }
}
