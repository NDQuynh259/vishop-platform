package com.vishop.iam.modules.identity.presentation.rest.dto;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.application.dto.UserView;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthenticationView(
    UserView user,
    UUID tenantId,
    UUID tenantRoleId,
    String tenantRole,
    Set<String> permissions,
    Instant accessExpiresAt,
    Instant refreshExpiresAt) {
  public static AuthenticationView from(AuthResult result) {
    return new AuthenticationView(
        result.user(),
        result.actor().tenantId(),
        result.tenantRoleId(),
        result.actor().tenantRole(),
        result.actor().permissions(),
        result.tokens().accessExpiresAt(),
        result.tokens().refreshExpiresAt());
  }
}
