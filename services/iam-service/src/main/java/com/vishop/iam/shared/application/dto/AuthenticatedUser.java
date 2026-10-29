package com.vishop.iam.shared.application.dto;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
  public void requireCms() {
    if (!SystemRole.PLATFORM_ADMIN.name().equals(systemRole))
      throw new ForbiddenException("CMS requires PLATFORM_ADMIN");
  }

  public void requireTenantAccess(UUID tenant, String permission) {
    if (tenantId == null || !tenantId.equals(tenant) || tenantRole == null)
      throw new ForbiddenException("Token does not grant access to this tenant");
    if (permission != null && !permissions.contains(permission))
      throw new ForbiddenException("Missing tenant permission");
  }

  public AuthenticatedUser {
    Objects.requireNonNull(userId);
    Objects.requireNonNull(systemRole);
    permissions = Set.copyOf(permissions);
  }
}
