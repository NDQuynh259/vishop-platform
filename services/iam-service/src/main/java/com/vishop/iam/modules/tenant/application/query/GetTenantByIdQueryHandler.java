package com.vishop.iam.modules.tenant.application.query;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetTenantByIdQueryHandler
    implements QueryHandler<GetTenantByIdQuery, TenantResponseDto> {
  private final TenantRepositoryPort tenantRepository;
  private final AccessApi authorization;

  public GetTenantByIdQueryHandler(TenantRepositoryPort tenantRepository, AccessApi authorization) {
    this.tenantRepository = tenantRepository;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public TenantResponseDto handle(GetTenantByIdQuery query) {
    var actor = query.actor();
    var id = query.id();

    authorization.requireAccess(actor.userId(), id, CommonPermissions.Tenant.VIEW);
    return TenantResponseMapper.from(tenantRepository.tenant(id));
  }
}
