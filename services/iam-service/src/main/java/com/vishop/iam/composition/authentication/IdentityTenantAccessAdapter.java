package com.vishop.iam.composition.authentication;

import com.vishop.iam.modules.identity.application.api.IdentityApi;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityTenantAccessAdapter implements IdentityApi.TenantAccessPort {
  private final TenantApi tenants;

  public IdentityTenantAccessAdapter(TenantApi tenants) {
    this.tenants = tenants;
  }

  public List<UUID> activeTenantIds(UUID userId) {
    return tenants.activeTenantIds(userId);
  }

  public Optional<IdentityApi.TenantGrant> activeGrant(UUID userId, UUID tenantId) {
    return tenants
        .authenticationGrant(userId, tenantId)
        .map(g -> new IdentityApi.TenantGrant(g.roleId(), g.roleKey(), g.permissions()));
  }
}
