package com.vishop.iam.modules.identity.domain.entity;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.constants.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String fullName,
    SystemRole systemRole,
    UserStatus status,
    Instant createdAt,
    UUID lastTenantId,
    long tokenVersion) {

  public void requireActive() {
    if (status != UserStatus.ACTIVE)
      throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
          "Account is not active");
  }
}
