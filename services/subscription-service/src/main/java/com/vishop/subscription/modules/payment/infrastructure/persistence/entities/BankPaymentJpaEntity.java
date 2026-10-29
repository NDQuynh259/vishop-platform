package com.vishop.subscription.modules.payment.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "bank_payments")
@Getter
@Setter
@NoArgsConstructor
public class BankPaymentJpaEntity {
  @Id
  @Column(nullable = false)
  private long id;

  @Column(nullable = true)
  private UUID orderId;

  private String accountNumber;
  private String bank;
  private String referenceCode;
  private long amountVnd;
  private String transferType;
  private String status;
  private String reviewReason;
  private Instant occurredAt;
  private Instant createdAt;
}
