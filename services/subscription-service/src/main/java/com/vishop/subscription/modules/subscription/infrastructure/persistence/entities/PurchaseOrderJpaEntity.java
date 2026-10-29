package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "billing_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID tenantId;

  private UUID actorId;
  private UUID priceId;
  private String planCode;
  private String planName;

  @Enumerated(EnumType.STRING)
  private BillingCycle cycle;

  private long amountVnd;
  private int shopLimit;
  private long monthlyOrders;
  private int additionalMembers;
  private String paymentCode;
  private String idempotencyKey;
  private long expectedRevision;
  private long receivedVnd;

  @Enumerated(EnumType.STRING)
  private PurchaseOrder.State state;

  private Instant createdAt;
  private Instant expiresAt;
  private Instant termStartsAt;
  private Instant termEndsAt;
  private String reviewReason;
}
