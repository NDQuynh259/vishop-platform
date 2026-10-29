package com.vishop.subscription.modules.payment.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "sepay_inbox")
@Getter
@Setter
@NoArgsConstructor
public class SePayInboxJpaEntity {
  @Id
  @Column(nullable = false)
  private long id;

  @Column(columnDefinition = "text", nullable = false)
  private String rawPayload;

  private String payloadHash;
  private String status;
  private String reviewReason;
  private Instant receivedAt;
  private Instant processedAt;
}
