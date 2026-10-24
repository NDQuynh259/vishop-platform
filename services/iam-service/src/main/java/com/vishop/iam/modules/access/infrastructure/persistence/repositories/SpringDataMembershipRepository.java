package com.vishop.iam.modules.access.infrastructure.persistence.repositories;

import com.vishop.iam.modules.access.infrastructure.persistence.entities.MembershipJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {
  Optional<MembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);

  @Query(
      value =
          "SELECT count(*) FROM tenant_members m JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id JOIN users u ON u.id=m.user_id WHERE m.tenant_id=:tenant AND m.status='ACTIVE' AND r.builtin_key='OWNER' AND u.status='ACTIVE'",
      nativeQuery = true)
  int activeOwners(@Param("tenant") UUID tenant);
}
