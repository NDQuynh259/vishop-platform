package com.vishop.iam.identity.domain.entities;

import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String fullName,
    SystemRole systemRole,
    String status,
    Instant createdAt) {

  public void requireActive() {
    if (!"ACTIVE".equals(status))
      throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
          "Account is not active");
  }
}
