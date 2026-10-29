package com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories;

import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PurchaseOrderJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderRepository extends JpaRepository<PurchaseOrderJpaEntity, UUID> {
  interface OrderReference {
    UUID getId();

    UUID getTenantId();
  }

  @Query(
      "select o.id as id,o.tenantId as tenantId from PurchaseOrderJpaEntity o where o.paymentCode=:code")
  Optional<OrderReference> paymentReference(@Param("code") String code);

  Optional<PurchaseOrderJpaEntity> findByTenantIdAndActorIdAndIdempotencyKey(
      UUID tenant, UUID actor, String key);

  @Query(
      "select o from PurchaseOrderJpaEntity o where o.tenantId=:tenant and o.orderStatus=com.vishop.subscription.modules.subscription.domain.valueobject.OrderStatus.OPEN and o.fulfillmentStatus=com.vishop.subscription.modules.subscription.domain.valueobject.FulfillmentStatus.PENDING")
  Optional<PurchaseOrderJpaEntity> open(@Param("tenant") UUID tenant);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrderJpaEntity o where o.id=:id")
  Optional<PurchaseOrderJpaEntity> lock(@Param("id") UUID id);

  Page<PurchaseOrderJpaEntity> findByTenantId(UUID tenant, Pageable page);
}
