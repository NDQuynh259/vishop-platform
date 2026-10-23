package com.vishop.iam.identity.application.command;

import com.vishop.iam.identity.application.dto.AuthenticationResult;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LoginCommand(String email, String password)
    implements Command<AuthenticationResult> {}
