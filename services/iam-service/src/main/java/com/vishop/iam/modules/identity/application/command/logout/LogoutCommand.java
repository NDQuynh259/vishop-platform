package com.vishop.iam.modules.identity.application.command.logout;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LogoutCommand(AuthenticatedUser actor) implements Command<Void> {}
