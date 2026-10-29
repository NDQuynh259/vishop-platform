package com.vishop.iam.modules.tenant.application.command.member;

import com.vishop.iam.modules.tenant.application.dto.InvitationCreated;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record InviteMemberCommand(AuthenticatedUser actor, UUID tenant, String email, UUID role)
    implements Command<InvitationCreated> {}
