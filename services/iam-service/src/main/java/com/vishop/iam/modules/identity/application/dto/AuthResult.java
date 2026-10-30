package com.vishop.iam.modules.identity.application.dto;

import com.vishop.iam.modules.identity.application.port.AuthTokens.Issued;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;

/** Nội bộ application. Controller chỉ xuất user/context, đưa credential vào HttpOnly cookie. */
public record AuthResult(UserView user, AuthenticatedUser actor, UUID tenantRoleId, Issued tokens) {
  @Override
  public String toString() {
    return "AuthResult[userId=" + user.id() + ", credentials=REDACTED]";
  }
}
