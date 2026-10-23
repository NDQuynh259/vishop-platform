package com.vishop.iam.identity.application.command;

import com.vishop.iam.identity.application.dto.AuthenticationResult;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RegisterUserCommand(
    String email, String password, String name, UUID requestedTenant, String requestedRole)
    implements Command<AuthenticationResult> {}
