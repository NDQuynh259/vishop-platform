package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.modules.access.application.dto.InvitationCreated;
import com.vishop.iam.modules.access.application.service.AccessAuthorizationService;
import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.port.InvitationRepositoryPort;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.access.domain.service.RoleAssignmentPolicy;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
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
  private final OpaqueTokenPort sessionTokens;
  private final AccessAuthorizationService authorization;
  private final RoleAssignmentPolicy rolePolicy;

  public InviteMemberCommandHandler(
      IamEventPublisherPort outbox,
      RoleRepositoryPort roleRepository,
      InvitationRepositoryPort invitationRepository,
      TenantApi tenants,
      OpaqueTokenPort sessionTokens,
      AccessAuthorizationService authorization,
      RoleAssignmentPolicy rolePolicy) {
    this.outbox = outbox;
    this.roleRepository = roleRepository;
    this.invitationRepository = invitationRepository;
    this.tenants = tenants;
    this.sessionTokens = sessionTokens;
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

    tenants.lockTenant(tenant);
    Membership manager =
        authorization.access(actor.userId(), tenant, CommonPermissions.Tenant.MEMBER_INVITE);
    rolePolicy.canAssign(manager, roleRepository.role(tenant, role));
    String raw = sessionTokens.generate();
    var invitation =
        invitationRepository.invite(
            tenant,
            email.trim().toLowerCase(Locale.ROOT),
            role,
            actor.userId(),
            sessionTokens.hash(raw));
    outbox.append(
        "MemberInvited",
        "Tenant",
        tenant,
        tenant,
        Map.of("actorId", actor.userId(), "subjectId", invitation.id()));
    return new InvitationCreated(invitation.id(), raw, invitation.expiresAt());
  }
}
