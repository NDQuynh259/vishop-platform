package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import io.github.coreplatform.common.domain.cqrs.Command;

public record RegisterCommand(String email, String password) implements Command<AuthResult> {
  @Override
  public String toString() {
    return "RegisterCommand[credentials=REDACTED]";
  }
}
