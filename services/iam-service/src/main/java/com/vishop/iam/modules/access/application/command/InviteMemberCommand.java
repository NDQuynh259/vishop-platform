package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.modules.access.application.dto.InvitationCreated;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record InviteMemberCommand(IamActor actor, UUID tenant, String email, UUID role)
    implements Command<InvitationCreated> {}
