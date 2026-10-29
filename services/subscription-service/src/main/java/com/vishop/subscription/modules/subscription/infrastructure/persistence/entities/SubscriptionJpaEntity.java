package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionJpaEntity {
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

  @Column(insertable = false, updatable = false)
  private java.time.Instant createdAt;

  @Column(insertable = false, updatable = false)
  private java.time.Instant updatedAt;
}
