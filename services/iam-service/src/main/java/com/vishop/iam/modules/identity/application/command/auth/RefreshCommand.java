package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RefreshCommand(String refreshToken, UUID tenantId) implements Command<AuthResult> {
  @Override
  public String toString() {
    return "RefreshCommand[credentials=REDACTED]";
  }
}
