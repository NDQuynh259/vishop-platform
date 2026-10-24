package com.vishop.iam.modules.access.application.service;

import com.vishop.iam.contracts.identity.IdentityApi;
import com.vishop.iam.contracts.tenant.TenantApi;
import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.port.MembershipRepositoryPort;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AccessAuthorizationService {
  private final IdentityApi identities;
  private final TenantApi tenants;
  private final MembershipRepositoryPort memberships;

  public AccessAuthorizationService(
      IdentityApi identities, TenantApi tenants, MembershipRepositoryPort memberships) {
    this.identities = identities;
    this.tenants = tenants;
    this.memberships = memberships;
  }

  public Membership access(UUID user, UUID tenant, String permission) {
    identities.requireActive(user);
    Membership member =
        memberships
            .membership(user, tenant)
            .orElseThrow(() -> new ForbiddenException("Not a member of this tenant"));
    if (!"ACTIVE".equals(member.status()) || !tenants.isActive(tenant))
      throw new ForbiddenException("Tenant membership is not active");
    if (member.requireTwoFactor())
      throw new ForbiddenException(
          "This role requires verified MFA; MFA authentication is not configured");
    if (permission != null && !member.permissions().contains(permission))
      throw new ForbiddenException("Missing tenant permission");
    return member;
  }
}
