package com.vishop.iam.modules.tenant.application.query.role;

import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTenantRolesQueryHandler
    implements QueryHandler<ListTenantRolesQuery, List<RoleResponseDto>> {
  private final RoleRepositoryPort roleRepository;
  private final TenantAuthorizationService authorization;

  public ListTenantRolesQueryHandler(
      RoleRepositoryPort roleRepository, TenantAuthorizationService authorization) {
    this.roleRepository = roleRepository;
    this.authorization = authorization;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleResponseDto> handle(ListTenantRolesQuery query) {
    var actor = query.actor();
    var tenant = query.tenant();

    authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_VIEW);
    return roleRepository.roles(tenant).stream().map(RoleResponseDto::from).toList();
  }
}
