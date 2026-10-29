package com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionAccountJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataSubscriptionAccountRepository
    extends JpaRepository<SubscriptionAccountJpaEntity, UUID> {
  @Modifying
  @Query(
      value =
          "INSERT INTO billing_accounts(tenant_id,plan_code,purchased_cycles,revision) VALUES (:tenant,'FREE',0,0) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int ensure(@Param("tenant") UUID tenant);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from SubscriptionAccountJpaEntity a where a.tenantId=:tenant")
  Optional<SubscriptionAccountJpaEntity> lock(@Param("tenant") UUID tenant);
}
