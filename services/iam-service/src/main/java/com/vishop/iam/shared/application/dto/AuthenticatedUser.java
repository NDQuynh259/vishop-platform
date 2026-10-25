package com.vishop.iam.shared.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId, UUID tenantId, String systemRole, List<String> roles, Set<String> permissions) {
  public AuthenticatedUser {
    Objects.requireNonNull(userId);
    Objects.requireNonNull(systemRole);
    roles = List.copyOf(roles);
    permissions = Set.copyOf(permissions);
  }
}
