package com.vishop.iam.modules.tenant.domain.entity;

import io.github.coreplatform.common.domain.constants.MembershipStatus;
import java.util.Set;
import java.util.UUID;

public record Membership(
    UUID id,
    UUID tenantId,
    UUID userId,
    UUID roleId,
    String roleKey,
    MembershipStatus status,
    boolean requireTwoFactor,
    Set<String> permissions) {
  public Membership {
    permissions = Set.copyOf(permissions);
  }
}
