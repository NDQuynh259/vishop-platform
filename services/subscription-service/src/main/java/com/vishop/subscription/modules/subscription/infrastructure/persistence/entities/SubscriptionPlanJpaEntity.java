package com.vishop.subscription.modules.subscription.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "subscription_plans")
@Getter
@NoArgsConstructor
public class SubscriptionPlanJpaEntity {
  @Id
  @Column(length = 30)
  private String code;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;
}
