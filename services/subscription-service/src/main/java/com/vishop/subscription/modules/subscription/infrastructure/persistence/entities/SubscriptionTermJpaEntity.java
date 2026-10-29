package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "subscription_terms")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionTermJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private UUID tenantId;

  private Instant startsAt;
  private Instant endsAt;
}
