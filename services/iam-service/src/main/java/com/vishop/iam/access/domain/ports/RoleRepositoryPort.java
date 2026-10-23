package com.vishop.iam.access.domain.ports;

import com.vishop.iam.access.domain.entities.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleRepositoryPort {
  Role role(UUID tenant, UUID role);

  UUID ownerRole(UUID tenant);

  List<Role> roles(UUID tenant);

  UUID createRole(UUID tenant, String name, Set<String> permissions);

  void updateRole(UUID tenant, UUID role, String name, Set<String> permissions);
}
