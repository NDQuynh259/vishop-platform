package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "billing_prices")
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
}
