package com.vishop.iam.infra.security;

import io.github.coreplatform.common.domain.constants.SystemRole;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Authorization uses only the principal reconstructed from authenticated gateway context. */
@Component("iamAuthorization")
public class IamAuthorization {
  public boolean canAccessCms(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof IamPrincipal principal
        && SystemRole.PLATFORM_ADMIN.name().equals(principal.systemRole());
  }

  public boolean inTenant(Authentication authentication, UUID tenantId) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof IamPrincipal principal
        && tenantId != null
        && tenantId.equals(principal.tenantId());
  }
}
