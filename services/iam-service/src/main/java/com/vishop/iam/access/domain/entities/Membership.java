package com.vishop.iam.access.domain.entities;

import java.util.Set;
import java.util.UUID;

public record Membership(
    UUID id,
    UUID tenantId,
    UUID userId,
    UUID roleId,
    String roleKey,
    String status,
    boolean requireTwoFactor,
    Set<String> permissions) {
  public Membership {
    permissions = Set.copyOf(permissions);
  }
}
