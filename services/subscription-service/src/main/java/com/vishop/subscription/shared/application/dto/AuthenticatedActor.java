package com.vishop.subscription.shared.application.dto;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.*;

public record AuthenticatedActor(
    UUID userId, UUID tenantId, String systemRole, Set<String> permissions) {
  public AuthenticatedActor {
    permissions = Set.copyOf(permissions);
  }

  public void require(String permission) {
    if (tenantId == null || !permissions.contains(permission))
      throw new ForbiddenException("Missing tenant billing permission");
  }

  public void requireCms() {
    if (!SystemRole.PLATFORM_ADMIN.name().equals(systemRole))
      throw new ForbiddenException("CMS requires PLATFORM_ADMIN");
  }
}
