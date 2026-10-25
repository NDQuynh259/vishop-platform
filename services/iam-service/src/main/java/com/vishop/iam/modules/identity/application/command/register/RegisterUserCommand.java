package com.vishop.iam.modules.identity.application.command.register;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RegisterUserCommand(
    String email, String password, String name, UUID requestedTenant, String requestedRole)
    implements Command<AuthResponse> {}
