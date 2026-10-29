package com.vishop.iam.modules.tenant.application.service;

import com.vishop.iam.modules.tenant.domain.valueobject.TenantAuthority;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthorizationService {
  public TenantAuthority access(AuthenticatedUser actor, UUID tenant, String permission) {
    actor.requireTenantAccess(tenant, permission);
    return new TenantAuthority(actor.tenantRole(), actor.permissions());
  }
}
