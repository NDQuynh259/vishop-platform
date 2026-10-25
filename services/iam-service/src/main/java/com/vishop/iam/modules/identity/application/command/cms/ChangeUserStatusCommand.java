package com.vishop.iam.modules.identity.application.command.cms;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeUserStatusCommand(AuthenticatedUser actor, UUID user, String status)
    implements Command<Void> {}
