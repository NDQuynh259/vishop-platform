package com.vishop.iam.access.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record DeactivateMemberCommand(IamActor actor, UUID tenant, UUID user)
    implements Command<Void> {}
