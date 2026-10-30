package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;

public record LogoutCommand(AuthenticatedUser actor, String refreshToken, String accessToken)
    implements Command<Void> {
  @Override
  public String toString() {
    return "LogoutCommand[credentials=REDACTED]";
  }
}
