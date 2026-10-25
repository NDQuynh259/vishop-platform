package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.modules.access.application.service.AccessAuthorizationService;
import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.access.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeactivateMemberCommandHandler
    implements CommandHandler<DeactivateMemberCommand, Void> {
  private final MembershipRepositoryPort membershipRepository;
  private final RoleRepositoryPort roleRepository;
  private final TenantApi tenants;
  private final IamEventPublisherPort outbox;
  private final AccessAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public DeactivateMemberCommandHandler(
      MembershipRepositoryPort membershipRepository,
      RoleRepositoryPort roleRepository,
      TenantApi tenants,
      IamEventPublisherPort outbox,
      AccessAuthorizationService authorization,
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
  public Void handle(DeactivateMemberCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var user = command.user();

    tenants.lockTenant(tenant);
    Membership manager =
        authorization.access(actor.userId(), tenant, CommonPermissions.Tenant.MEMBER_REMOVE);
    Membership member =
        membershipRepository
            .membership(user, tenant)
            .orElseThrow(() -> new EntityNotFoundException("Member", user));
    rolePolicy.protectOwnerManagement(manager, member);
    rolePolicy.protectOwner(
        tenant, member, roleRepository.role(tenant, member.roleId()).builtinKey(), "INACTIVE");
    membershipRepository.updateMembership(user, tenant, member.roleId(), "INACTIVE");
    outbox.append(
        "MemberDeactivated",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", user));

    return null;
  }
}
