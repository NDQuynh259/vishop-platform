package com.vishop.iam.modules.tenant.application.command.invitation;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.domain.entity.Membership;
import com.vishop.iam.modules.tenant.domain.port.InvitationRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AcceptInvitationCommandHandler
    implements CommandHandler<AcceptInvitationCommand, TenantResponseDto> {
  private final IdentityApi identities;
  private final IamEventPublisherPort outbox;
  private final MembershipRepositoryPort membershipRepository;
  private final RoleRepositoryPort roleRepository;
  private final InvitationRepositoryPort invitationRepository;
  private final TenantApi tenants;
  private final OpaqueTokenPort invitationTokens;
  private final RoleAssignmentPolicy rolePolicy;

  public AcceptInvitationCommandHandler(
      IdentityApi identities,
      IamEventPublisherPort outbox,
      MembershipRepositoryPort membershipRepository,
      RoleRepositoryPort roleRepository,
      InvitationRepositoryPort invitationRepository,
      TenantApi tenants,
      OpaqueTokenPort invitationTokens,
      RoleAssignmentPolicy rolePolicy) {
    this.identities = identities;
    this.outbox = outbox;
    this.membershipRepository = membershipRepository;
    this.roleRepository = roleRepository;
    this.invitationRepository = invitationRepository;
    this.tenants = tenants;
    this.invitationTokens = invitationTokens;
    this.rolePolicy = rolePolicy;
  }

  @Override
  @Transactional
  public TenantResponseDto handle(AcceptInvitationCommand command) {
    var actor = command.actor();
    var token = command.token();

    UserView user = identities.requireActive(actor.userId());
    var invitation =
        invitationRepository
            .invitation(invitationTokens.hash(token))
            .orElseThrow(() -> new BadRequestException("Invalid or expired invitation"));
    tenants.lockTenant(invitation.tenantId());
    invitation =
        invitationRepository
            .invitation(invitationTokens.hash(token))
            .orElseThrow(() -> new BadRequestException("Invalid or expired invitation"));
    if (!invitation.email().equalsIgnoreCase(user.email()))
      throw new ForbiddenException("Invitation email does not match your account");
    if (!tenants.isActive(invitation.tenantId()))
      throw new ForbiddenException("Tenant is not active");
    Membership inviter =
        membershipRepository
            .membership(invitation.invitedBy(), invitation.tenantId())
            .filter(
                member ->
                    "ACTIVE".equals(member.status())
                        && !member.requireTwoFactor()
                        && member.permissions().contains(CommonPermissions.Tenant.MEMBER_INVITE))
            .orElseThrow(() -> new ForbiddenException("Invitation issuer is no longer authorized"));
    rolePolicy.canAssign(
        new TenantAuthority(
            roleRepository.role(invitation.tenantId(), inviter.roleId()).builtinKey(),
            inviter.permissions()),
        roleRepository.role(invitation.tenantId(), invitation.roleId()));
    var existing = membershipRepository.membership(user.id(), invitation.tenantId());
    if (existing.isPresent() && "ACTIVE".equals(existing.get().status()))
      throw new ConflictException("Already an active member");
    if (existing.isPresent())
      membershipRepository.updateMembership(
          user.id(), invitation.tenantId(), invitation.roleId(), "ACTIVE");
    else membershipRepository.addMembership(user.id(), invitation.tenantId(), invitation.roleId());
    invitationRepository.acceptInvitation(invitation.id(), user.id());
    outbox.append(
        "InvitationAccepted",
        "Tenant",
        invitation.tenantId(),
        invitation.tenantId(),
        Map.of("actorId", actor.userId(), "subjectId", invitation.id()));
    return tenants.view(invitation.tenantId());
  }
}
