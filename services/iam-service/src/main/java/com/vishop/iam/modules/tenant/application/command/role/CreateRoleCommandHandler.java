package com.vishop.iam.modules.tenant.application.command.role;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateRoleCommandHandler
    implements CommandHandler<CreateRoleCommand, RoleResponseDto> {
  private final RoleRepositoryPort roleRepository;
  private final TenantApi tenants;
  private final IamEventPublisherPort outbox;
  private final TenantAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public CreateRoleCommandHandler(
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
  public RoleResponseDto handle(CreateRoleCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var name = command.name();
    var permissions = command.permissions();

    TenantAuthority member =
        authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_ROLE_ASSIGN);
    tenants.lockTenant(tenant);
    rolePolicy.validateGrants(member, permissions);
    UUID role = roleRepository.createRole(tenant, name.trim(), permissions);
    outbox.append(
        "RoleCreated",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", role));
    return RoleResponseDto.from(roleRepository.role(tenant, role));
  }
}
