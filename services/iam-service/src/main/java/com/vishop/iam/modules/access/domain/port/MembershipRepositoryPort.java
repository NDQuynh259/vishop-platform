package com.vishop.iam.modules.access.domain.port;

import com.vishop.iam.modules.access.domain.entity.Membership;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepositoryPort {
  Optional<Membership> membership(UUID user, UUID tenant);

  void addMembership(UUID user, UUID tenant, UUID role);

  void updateMembership(UUID user, UUID tenant, UUID role, String status);

  int activeOwners(UUID tenant);
}
