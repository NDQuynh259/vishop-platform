package com.vishop.iam.modules.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.RoleJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {
  @EntityGraph(attributePaths = "permissions")
  Optional<RoleJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  @EntityGraph(attributePaths = "permissions")
  List<RoleJpaEntity> findByTenantIdOrderByName(UUID tenantId);

  @Query("select r.id from RoleJpaEntity r where r.tenantId=:tenant and r.builtinKey='OWNER'")
  UUID ownerRole(@Param("tenant") UUID tenant);
}
