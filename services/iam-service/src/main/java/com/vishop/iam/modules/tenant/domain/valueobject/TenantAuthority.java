package com.vishop.iam.modules.tenant.domain.valueobject;

import java.util.Set;

/** Tenant grants captured when an access token or an invitation is validated. */
public record TenantAuthority(String roleKey, Set<String> permissions) {
  public TenantAuthority {
    permissions = Set.copyOf(permissions);
  }
}
