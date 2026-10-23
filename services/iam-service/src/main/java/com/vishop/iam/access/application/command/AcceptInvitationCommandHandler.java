package com.vishop.iam.access.application.command;

import com.vishop.iam.access.application.service.AccessAuthorizationService;
import com.vishop.iam.access.domain.entities.Membership;
import com.vishop.iam.access.domain.ports.InvitationRepositoryPort;
import com.vishop.iam.access.domain.ports.MembershipRepositoryPort;
import com.vishop.iam.access.domain.ports.RoleRepositoryPort;
import com.vishop.iam.access.domain.services.RoleAssignmentPolicy;
import com.vishop.iam.identity.application.api.IdentityApi;
import com.vishop.iam.identity.application.dto.UserView;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import com.vishop.iam.tenant.application.api.TenantApi;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
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
  private final OpaqueTokenPort sessionTokens;
  private final AccessAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public AcceptInvitationCommandHandler(
      IdentityApi identities,
      IamEventPublisherPort outbox,
      MembershipRepositoryPort membershipRepository,
      RoleRepositoryPort roleRepository,
      InvitationRepositoryPort invitationRepository,
      TenantApi tenants,
      OpaqueTokenPort sessionTokens,
      AccessAuthorizationService authorization,
      RoleAssignmentPolicy rolePolicy) {
    this.identities = identities;
    this.outbox = outbox;
    this.membershipRepository = membershipRepository;
    this.roleRepository = roleRepository;
    this.invitationRepository = invitationRepository;
    this.tenants = tenants;
    this.sessionTokens = sessionTokens;
    this.authorization = authorization;
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
            .invitation(sessionTokens.hash(token))
            .orElseThrow(() -> new BadRequestException("Invalid or expired invitation"));
    tenants.lockTenant(invitation.tenantId());
    invitation =
        invitationRepository
            .invitation(sessionTokens.hash(token))
            .orElseThrow(() -> new BadRequestException("Invalid or expired invitation"));
    if (!invitation.email().equalsIgnoreCase(user.email()))
      throw new ForbiddenException("Invitation email does not match your account");
    if (!tenants.isActive(invitation.tenantId()))
      throw new ForbiddenException("Tenant is not active");
    Membership inviter =
        authorization.access(
            invitation.invitedBy(), invitation.tenantId(), CommonPermissions.Tenant.MEMBER_INVITE);
    rolePolicy.canAssign(inviter, roleRepository.role(invitation.tenantId(), invitation.roleId()));
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
