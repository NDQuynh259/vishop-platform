package com.vishop.iam.modules.tenant.application.command.member;

import com.vishop.iam.modules.tenant.application.api.TenantApi;
import com.vishop.iam.modules.tenant.application.dto.InvitationCreated;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.port.InvitationRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InviteMemberCommandHandler
    implements CommandHandler<InviteMemberCommand, InvitationCreated> {
  private final IamEventPublisherPort outbox;
  private final RoleRepositoryPort roleRepository;
  private final InvitationRepositoryPort invitationRepository;
  private final TenantApi tenants;
  private final OpaqueTokenPort invitationTokens;
  private final TenantAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public InviteMemberCommandHandler(
      IamEventPublisherPort outbox,
      RoleRepositoryPort roleRepository,
      InvitationRepositoryPort invitationRepository,
      TenantApi tenants,
      OpaqueTokenPort invitationTokens,
      TenantAuthorizationService authorization,
      RoleAssignmentPolicy rolePolicy) {
    this.outbox = outbox;
    this.roleRepository = roleRepository;
    this.invitationRepository = invitationRepository;
    this.tenants = tenants;
    this.invitationTokens = invitationTokens;
    this.authorization = authorization;
    this.rolePolicy = rolePolicy;
  }

  @Override
  @Transactional
  public InvitationCreated handle(InviteMemberCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();
    var email = command.email();
    var role = command.role();

    TenantAuthority manager =
        authorization.access(actor, tenant, CommonPermissions.Tenant.MEMBER_INVITE);
    tenants.lockTenant(tenant);
    rolePolicy.canAssign(manager, roleRepository.role(tenant, role));
    String raw = invitationTokens.generate();
    var invitation =
        invitationRepository.invite(
            tenant,
            email.trim().toLowerCase(Locale.ROOT),
            role,
            actor.userId(),
            invitationTokens.hash(raw));
    outbox.append(
        "MemberInvited",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", invitation.id()));
    return new InvitationCreated(invitation.id(), raw, invitation.expiresAt());
  }
}
