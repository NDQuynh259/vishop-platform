package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "billing_accounts")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionAccountJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID tenantId;

  private String planCode;

  @Enumerated(EnumType.STRING)
  private BillingCycle cycle;

  private Instant anchorAt;
  private int purchasedCycles;
  private Instant endsAt;
  private long revision;
}
