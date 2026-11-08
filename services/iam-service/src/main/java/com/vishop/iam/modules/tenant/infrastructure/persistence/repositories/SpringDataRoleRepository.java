package com.vishop.iam.modules.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.RoleJpaEntity;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {
  @EntityGraph(attributePaths = "permissions")
  Optional<RoleJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Page<RoleJpaEntity> findByTenantIdOrderByNameAscIdAsc(UUID tenantId, Pageable pagination);

  long countByTenantId(UUID tenantId);

  @Query("select r.id from RoleJpaEntity r where r.tenantId=:tenant and r.builtinKey='OWNER'")
  UUID ownerRole(@Param("tenant") UUID tenant);
}
