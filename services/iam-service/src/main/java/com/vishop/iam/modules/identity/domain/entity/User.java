package com.vishop.iam.modules.identity.domain.entity;

import io.github.coreplatform.common.domain.constants.SystemRole;
import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String fullName,
    SystemRole systemRole,
    String status,
    Instant createdAt,
    UUID lastTenantId) {

  public void requireActive() {
    if (!"ACTIVE".equals(status))
      throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
          "Account is not active");
  }
}
