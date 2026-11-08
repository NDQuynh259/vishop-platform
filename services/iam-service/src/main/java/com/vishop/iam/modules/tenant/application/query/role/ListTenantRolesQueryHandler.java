package com.vishop.iam.modules.tenant.application.query.role;

import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTenantRolesQueryHandler
    implements QueryHandler<ListTenantRolesQuery, PagedResult<RoleResponseDto>> {
  private final RoleRepositoryPort roleRepository;
  private final TenantAuthorizationService authorization;

  public ListTenantRolesQueryHandler(
      RoleRepositoryPort roleRepository, TenantAuthorizationService authorization) {
    this.roleRepository = roleRepository;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<RoleResponseDto> handle(ListTenantRolesQuery query) {
    var actor = query.actor();
    var tenant = query.tenant();

    authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_VIEW);
    var page = roleRepository.roles(tenant, query.pagination());
    return page.map(RoleResponseDto::from);
  }
}
