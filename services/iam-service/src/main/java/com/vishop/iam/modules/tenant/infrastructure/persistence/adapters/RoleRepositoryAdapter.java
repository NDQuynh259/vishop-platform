package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.domain.entity.Role;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.RoleJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataRoleRepository;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.persistence.pagination.JpaListPagination;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryAdapter implements RoleRepositoryPort {
  private final SpringDataRoleRepository roles;
  private final EntityManager entityManager;

  public RoleRepositoryAdapter(SpringDataRoleRepository roles, EntityManager entityManager) {
    this.roles = roles;
    this.entityManager = entityManager;
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

  public PagedResult<Role> roles(UUID tenant, ListPagination pagination) {
    return JpaListPagination.find(
        entityManager,
        RoleJpaEntity.class,
        (root, query, cb) -> cb.equal(root.get("tenantId"), tenant),
        Sort.by("name", "id"),
        pagination,
        "roles:" + tenant,
        RoleJpaEntity::toDomain);
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
