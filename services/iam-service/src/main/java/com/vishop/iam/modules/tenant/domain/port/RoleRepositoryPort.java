package com.vishop.iam.modules.tenant.domain.port;

import com.vishop.iam.modules.tenant.domain.entity.Role;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.Set;
import java.util.UUID;

public interface RoleRepositoryPort {
  Role role(UUID tenant, UUID role);

  UUID ownerRole(UUID tenant);

  PagedResult<Role> roles(UUID tenant, ListPagination pagination);

  UUID createRole(UUID tenant, String name, Set<String> permissions);

  void updateRole(UUID tenant, UUID role, String name, Set<String> permissions);
}
