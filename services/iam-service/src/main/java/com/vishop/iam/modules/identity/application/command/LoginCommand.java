package com.vishop.iam.modules.identity.application.command;

import com.vishop.iam.modules.identity.application.dto.AuthenticationResult;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LoginCommand(String email, String password)
    implements Command<AuthenticationResult> {}
