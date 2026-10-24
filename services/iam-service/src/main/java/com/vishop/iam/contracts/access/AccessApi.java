package com.vishop.iam.contracts.access;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccessApi {
  record Grant(String roleKey, Set<String> permissions) {
    public Grant {
      permissions = Set.copyOf(permissions);
    }
  }

  void requireAccess(UUID user, UUID tenant, String permission);

  List<UUID> activeTenantIds(UUID user);

  Optional<Grant> activeGrant(UUID user, UUID tenant);

  /** Adds the OWNER membership inside the caller's existing transaction. */
  void initializeOwner(UUID user, UUID tenant);
}
