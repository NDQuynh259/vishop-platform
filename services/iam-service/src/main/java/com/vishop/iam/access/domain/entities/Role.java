package com.vishop.iam.access.domain.entities;

import java.util.Set;
import java.util.UUID;

public record Role(
    UUID id,
    UUID tenantId,
    String name,
    String builtinKey,
    boolean requireTwoFactor,
    Set<String> permissions) {
  public Role {
    permissions = Set.copyOf(permissions);
  }
}
