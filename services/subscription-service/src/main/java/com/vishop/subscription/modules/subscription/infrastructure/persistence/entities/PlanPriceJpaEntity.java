package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "subscription_prices")
@Getter
@Setter
@NoArgsConstructor
public class PlanPriceJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  private String planCode;
  private String name;

  @Enumerated(EnumType.STRING)
  private BillingCycle cycle;

  private long amountVnd;
  private int shopLimit;
  private long monthlyOrders;
  private int additionalMembers;
  private boolean active;

  @Column(insertable = false, updatable = false)
  private java.time.Instant createdAt;

  @Column(nullable = false, length = 3)
  private String currency = "VND";
}
