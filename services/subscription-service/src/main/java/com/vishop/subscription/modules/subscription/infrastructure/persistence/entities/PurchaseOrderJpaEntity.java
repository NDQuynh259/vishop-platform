package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.valueobject.*;
import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "subscription_orders")
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
  private OrderStatus orderStatus;

  @Enumerated(EnumType.STRING)
  private PaymentStatus paymentStatus;

  @Enumerated(EnumType.STRING)
  private FulfillmentStatus fulfillmentStatus;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(insertable = false, updatable = false)
  private Instant updatedAt;

  private Instant createdAt;
  private Instant expiresAt;
  private Instant termStartsAt;
  private Instant termEndsAt;
  private String reviewReason;
}
