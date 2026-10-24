package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeMemberRoleCommand(IamActor actor, UUID tenant, UUID user, UUID role)
    implements Command<Void> {}
