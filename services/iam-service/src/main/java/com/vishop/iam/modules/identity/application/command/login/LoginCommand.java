package com.vishop.iam.modules.identity.application.command.login;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LoginCommand(String email, String password) implements Command<AuthResponse> {}
