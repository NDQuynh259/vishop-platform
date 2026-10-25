package com.vishop.iam.modules.access.application.api;

import com.vishop.iam.modules.access.application.port.AccessReadPort;
import com.vishop.iam.modules.access.application.service.AccessAuthorizationService;
import com.vishop.iam.modules.access.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.application.api.TenantApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccessApplicationApi implements AccessApi {
  private final MembershipRepositoryPort memberships;
  private final RoleRepositoryPort roles;
  private final AccessReadPort reads;
  private final AccessAuthorizationService authorization;
  private final TenantApi tenants;

  public AccessApplicationApi(
      MembershipRepositoryPort memberships,
      RoleRepositoryPort roles,
      AccessReadPort reads,
      AccessAuthorizationService authorization,
      TenantApi tenants) {
    this.memberships = memberships;
    this.roles = roles;
    this.reads = reads;
    this.authorization = authorization;
    this.tenants = tenants;
  }

  public void requireAccess(UUID user, UUID tenant, String permission) {
    authorization.access(user, tenant, permission);
  }

  public List<UUID> activeTenantIds(UUID user) {
    return reads.myTenants(user).stream().map(item -> item.id()).toList();
  }

  public Optional<Grant> activeGrant(UUID user, UUID tenant) {
    return memberships
        .membership(user, tenant)
        .filter(
            member ->
                "ACTIVE".equals(member.status())
                    && !member.requireTwoFactor()
                    && tenants.isActive(tenant))
        .map(member -> new Grant(member.roleKey(), member.permissions()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void initializeOwner(UUID user, UUID tenant) {
    memberships.addMembership(user, tenant, roles.ownerRole(tenant));
  }
}
