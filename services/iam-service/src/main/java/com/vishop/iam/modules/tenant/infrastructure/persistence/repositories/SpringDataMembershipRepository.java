package com.vishop.iam.modules.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {
  Optional<MembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}
