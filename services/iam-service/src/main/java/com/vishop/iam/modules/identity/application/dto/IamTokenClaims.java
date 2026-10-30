package com.vishop.iam.modules.identity.application.dto;

import io.github.coreplatform.common.domain.constants.SystemRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** IAM credential data only. Verification and authorization are separate operations. */
public final class IamTokenClaims {
  private IamTokenClaims() {}

  public record AccessToken(
      UUID userId,
      UUID tenantId,
      SystemRole systemRole,
      UUID tenantRoleId,
      String tenantRole,
      long tokenVersion) {
    public AccessToken {
      Objects.requireNonNull(userId, "userId");
      Objects.requireNonNull(systemRole, "systemRole");
      if (tokenVersion < 1) {
        throw new IllegalArgumentException("Invalid credential version");
      }
      if (tenantId == null) {
        if (tenantRoleId != null || tenantRole != null) {
          throw new IllegalArgumentException("Role requires a selected tenant");
        }
      } else if (tenantRoleId == null || tenantRole == null || tenantRole.isBlank()) {
        throw new IllegalArgumentException("Selected tenant requires a role ID and role key");
      }
    }
  }

  public record RefreshToken(UUID userId, UUID tenantId, long tokenVersion, Instant expiresAt) {
    public RefreshToken {
      Objects.requireNonNull(userId, "userId");
      Objects.requireNonNull(expiresAt, "expiresAt");
      if (tokenVersion < 1) {
        throw new IllegalArgumentException("Invalid credential version");
      }
    }
  }
}
