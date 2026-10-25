package com.vishop.iam.modules.identity.application.command.logout;

import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LogoutCommand(IamActor actor) implements Command<Void> {}
