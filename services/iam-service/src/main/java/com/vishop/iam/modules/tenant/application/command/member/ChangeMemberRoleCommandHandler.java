package com.vishop.iam.modules.tenant.application.command.member;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.entity.Membership;
import com.vishop.iam.modules.tenant.domain.entity.Role;
import com.vishop.iam.modules.tenant.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChangeMemberRoleCommandHandler
    implements CommandHandler<ChangeMemberRoleCommand, Void> {
  private final MembershipRepositoryPort membershipRepository;
  private final RoleRepositoryPort roleRepository;
  private final TenantApi tenants;
  private final IamEventPublisherPort outbox;
  private final TenantAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public ChangeMemberRoleCommandHandler(
      MembershipRepositoryPort membershipRepository,
      RoleRepositoryPort roleRepository,
      TenantApi tenants,
      IamEventPublisherPort outbox,
      TenantAuthorizationService authorization,
      RoleAssignmentPolicy rolePolicy) {
    this.membershipRepository = membershipRepository;
    this.roleRepository = roleRepository;
    this.tenants = tenants;
    this.outbox = outbox;
    this.authorization = authorization;
    this.rolePolicy = rolePolicy;
  }

  @Override
  @Transactional
  public Void handle(ChangeMemberRoleCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var user = command.user();
    var role = command.role();

    TenantAuthority manager =
        authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_ROLE_ASSIGN);
    tenants.lockTenant(tenant);
    Role target = roleRepository.role(tenant, role);
    rolePolicy.canAssign(manager, target);
    Membership member =
        membershipRepository
            .membership(user, tenant)
            .orElseThrow(() -> new EntityNotFoundException("Member", user));
    rolePolicy.protectOwnerManagement(manager, member);
    rolePolicy.protectOwner(tenant, member, target.builtinKey(), member.status());
    membershipRepository.updateMembership(user, tenant, role, member.status());
    outbox.append(
        "MemberRoleChanged",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", user));

    return null;
  }
}
