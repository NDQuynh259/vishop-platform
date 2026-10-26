package com.vishop.iam.modules.identity.application.command.register;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.domain.cqrs.Command;

public record RegisterUserCommand(String email, String password) implements Command<AuthResponse> {}
