package com.vishop.iam.shared.application.dto;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
  public AuthenticatedUser {
    Objects.requireNonNull(userId);
    Objects.requireNonNull(systemRole);
    permissions = Set.copyOf(permissions);
  }
}
