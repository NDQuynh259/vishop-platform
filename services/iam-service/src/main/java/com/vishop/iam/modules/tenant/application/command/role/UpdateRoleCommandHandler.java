package com.vishop.iam.modules.tenant.application.command.role;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.entity.Role;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateRoleCommandHandler
    implements CommandHandler<UpdateRoleCommand, RoleResponseDto> {
  private final RoleRepositoryPort roleRepository;
  private final TenantApi tenants;
  private final IamEventPublisherPort outbox;
  private final TenantAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public UpdateRoleCommandHandler(
      RoleRepositoryPort roleRepository,
      TenantApi tenants,
      IamEventPublisherPort outbox,
      TenantAuthorizationService authorization,
      RoleAssignmentPolicy rolePolicy) {
    this.roleRepository = roleRepository;
    this.tenants = tenants;
    this.outbox = outbox;
    this.authorization = authorization;
    this.rolePolicy = rolePolicy;
  }

  @Override
  @Transactional
  public RoleResponseDto handle(UpdateRoleCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var role = command.role();
    var name = command.name();
    var permissions = command.permissions();

    TenantAuthority member =
        authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_ROLE_ASSIGN);
    tenants.lockTenant(tenant);
    Role existing = roleRepository.role(tenant, role);
    if ("OWNER".equals(existing.builtinKey()))
      throw new ForbiddenException("The built-in OWNER role cannot be edited");
    rolePolicy.validateGrants(member, existing.permissions());
    rolePolicy.validateGrants(member, permissions);
    roleRepository.updateRole(tenant, role, name.trim(), permissions);
    outbox.append(
        "RoleUpdated",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", role));
    return RoleResponseDto.from(roleRepository.role(tenant, role));
  }
}
