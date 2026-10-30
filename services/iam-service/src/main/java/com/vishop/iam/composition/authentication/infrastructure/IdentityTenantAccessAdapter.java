package com.vishop.iam.composition.authentication.infrastructure;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantGrant;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Wires identity's outbound port to tenant without a reverse module dependency. */
@Component
public class IdentityTenantAccessAdapter implements TenantAccessPort {
  private final TenantApi tenants;

  public IdentityTenantAccessAdapter(TenantApi tenants) {
    this.tenants = tenants;
  }

  public List<UUID> activeTenantIds(UUID userId) {
    return tenants.activeTenantIds(userId);
  }

  public Optional<TenantGrant> activeGrant(UUID userId, UUID tenantId) {
    return tenants
        .activeGrant(userId, tenantId)
        .map(grant -> new TenantGrant(grant.roleId(), grant.roleKey(), grant.permissions()));
  }

  @Override
  public boolean hasActiveRole(UUID userId, UUID tenantId, UUID roleId, String roleKey) {
    return tenants.hasActiveRole(userId, tenantId, roleId, roleKey);
  }
}
