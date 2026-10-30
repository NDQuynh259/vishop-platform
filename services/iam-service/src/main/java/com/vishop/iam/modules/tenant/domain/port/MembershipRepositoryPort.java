package com.vishop.iam.modules.tenant.domain.port;

import com.vishop.iam.modules.tenant.domain.entity.Membership;
import java.util.Optional;
import java.util.UUID;

/** Membership persistence owned by access; this port is not a cross-module contract. */
public interface MembershipRepositoryPort {
  Optional<Membership> membership(UUID user, UUID tenant);

  boolean hasActiveRole(UUID user, UUID tenant, UUID role, String roleKey);

  void addMembership(UUID user, UUID tenant, UUID role);

  void updateMembership(UUID user, UUID tenant, UUID role, String status);

  int activeOwners(UUID tenant);
}
