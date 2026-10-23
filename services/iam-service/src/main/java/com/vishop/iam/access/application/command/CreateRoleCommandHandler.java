package com.vishop.iam.access.application.command;

import com.vishop.iam.access.application.dto.RoleResponseDto;
import com.vishop.iam.access.application.service.AccessAuthorizationService;
import com.vishop.iam.access.domain.entities.Membership;
import com.vishop.iam.access.domain.ports.RoleRepositoryPort;
import com.vishop.iam.access.domain.services.RoleAssignmentPolicy;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.tenant.application.api.TenantApi;
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
  private final AccessAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public CreateRoleCommandHandler(
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
  public RoleResponseDto handle(CreateRoleCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var name = command.name();
    var permissions = command.permissions();

    tenants.lockTenant(tenant);
    Membership member =
        authorization.access(actor.userId(), tenant, CommonPermissions.Tenant.MEMBER_ROLE_ASSIGN);
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
