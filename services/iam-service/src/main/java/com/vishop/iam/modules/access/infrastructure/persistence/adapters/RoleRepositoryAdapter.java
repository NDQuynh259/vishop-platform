package com.vishop.iam.modules.access.infrastructure.persistence.adapters;

import com.vishop.iam.modules.access.domain.entity.Role;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.access.infrastructure.persistence.entities.RoleJpaEntity;
import com.vishop.iam.modules.access.infrastructure.persistence.repositories.SpringDataRoleRepository;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryAdapter implements RoleRepositoryPort {
  private final SpringDataRoleRepository roles;

  public RoleRepositoryAdapter(SpringDataRoleRepository roles) {
    this.roles = roles;
  }

  public Role role(UUID tenant, UUID role) {
    return roles
        .findByTenantIdAndId(tenant, role)
        .orElseThrow(() -> new EntityNotFoundException("Role", role))
        .toDomain();
  }

  public UUID ownerRole(UUID tenant) {
    return roles.ownerRole(tenant);
  }

  public List<Role> roles(UUID tenant) {
    return roles.findByTenantIdOrderByName(tenant).stream().map(RoleJpaEntity::toDomain).toList();
  }

  public UUID createRole(UUID tenant, String name, Set<String> permissions) {
    var id = UuidV7.nextId();
    roles.saveAndFlush(RoleJpaEntity.create(id, tenant, name, permissions));
    return id;
  }

  public void updateRole(UUID tenant, UUID role, String name, Set<String> permissions) {
    var entity =
        roles
            .findByTenantIdAndId(tenant, role)
            .orElseThrow(() -> new EntityNotFoundException("Role", role));
    entity.update(name, permissions);
    roles.saveAndFlush(entity);
  }
}
