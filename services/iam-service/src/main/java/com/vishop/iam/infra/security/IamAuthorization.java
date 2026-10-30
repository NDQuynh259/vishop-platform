package com.vishop.iam.infra.security;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Điều kiện coarse-grained ở controller; use case vẫn kiểm tra quyền trên tài nguyên cụ thể. */
@Component("iamAuthorization")
public class IamAuthorization {
  public boolean canAccessCms(Authentication auth) {
    return auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof AuthenticatedUser user
        && SystemRole.PLATFORM_ADMIN.name().equals(user.systemRole());
  }

  public boolean inTenant(Authentication auth, UUID tenant) {
    return auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof AuthenticatedUser user
        && tenant != null
        && tenant.equals(user.tenantId());
  }
}
