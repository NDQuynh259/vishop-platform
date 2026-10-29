package com.vishop.subscription.modules.payment.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocationJpaEntity {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID paymentId;

  @Column(nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private long amountVnd;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(nullable = false)
  private Instant createdAt;
}
