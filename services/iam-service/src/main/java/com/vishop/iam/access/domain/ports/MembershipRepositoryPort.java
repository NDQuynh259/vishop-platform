package com.vishop.iam.access.domain.ports;

import com.vishop.iam.access.domain.entities.Membership;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepositoryPort {
  Optional<Membership> membership(UUID user, UUID tenant);

  void addMembership(UUID user, UUID tenant, UUID role);

  void updateMembership(UUID user, UUID tenant, UUID role, String status);

  int activeOwners(UUID tenant);
}
