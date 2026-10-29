package com.vishop.iam.modules.tenant.domain.entity;

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
