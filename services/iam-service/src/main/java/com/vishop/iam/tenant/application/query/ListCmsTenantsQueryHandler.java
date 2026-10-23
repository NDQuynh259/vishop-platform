package com.vishop.iam.tenant.application.query;

import com.vishop.iam.identity.application.api.IdentityApi;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.domain.ports.TenantRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsTenantsQueryHandler
    implements QueryHandler<ListCmsTenantsQuery, List<TenantResponseDto>> {
  private final TenantRepositoryPort tenantRepository;
  private final IdentityApi authorization;

  public ListCmsTenantsQueryHandler(
      TenantRepositoryPort tenantRepository, IdentityApi authorization) {
    this.tenantRepository = tenantRepository;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantResponseDto> handle(ListCmsTenantsQuery query) {
    var actor = query.actor();
    var limit = query.limit();
    var offset = query.offset();

    authorization.requireCms(actor);
    return tenantRepository.tenants(limit, offset).stream().map(TenantResponseDto::from).toList();
  }
}
