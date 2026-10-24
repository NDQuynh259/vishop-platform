package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.contracts.tenant.TenantApi;
import com.vishop.iam.modules.access.application.dto.RoleResponseDto;
import com.vishop.iam.modules.access.application.service.AccessAuthorizationService;
import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.entity.Role;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.access.domain.service.RoleAssignmentPolicy;
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
  private final AccessAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public UpdateRoleCommandHandler(
      RoleRepositoryPort roleRepository,
      TenantApi tenants,
      IamEventPublisherPort outbox,
      AccessAuthorizationService authorization,
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

    tenants.lockTenant(tenant);
    Membership member =
        authorization.access(actor.userId(), tenant, CommonPermissions.Tenant.MEMBER_ROLE_ASSIGN);
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
