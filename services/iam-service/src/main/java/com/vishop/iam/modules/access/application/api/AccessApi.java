package com.vishop.iam.modules.access.application.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public in-process contract for tenant access. Other modules use this interface instead of access
 * repositories, read ports, entities or the internal Spring implementation.
 */
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
