package com.vishop.subscription.modules.payment.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransactionJpaEntity {
  @Id private UUID id;

  @Column(nullable = false, length = 30)
  private String provider = "SEPAY";

  @Column(nullable = false)
  private long providerTransactionId;

  private UUID matchedOrderId;
  private UUID matchedTenantId;

  @Column(nullable = false, length = 100)
  private String accountNumber;

  @Column(nullable = false, length = 100)
  private String bank;

  @Column(length = 200)
  private String referenceCode;

  @Column(length = 100)
  private String subAccount;

  @Column(length = 100)
  private String paymentCode;

  private long amountVnd;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(insertable = false, updatable = false)
  private long allocatedVnd;

  private String transferType;
  private String status;
  private String reviewReason;
  private Instant occurredAt;
  private Instant createdAt;
}
