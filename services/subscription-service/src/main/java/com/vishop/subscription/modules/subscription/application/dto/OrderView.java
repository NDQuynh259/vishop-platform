package com.vishop.subscription.modules.subscription.application.dto;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import java.time.Instant;
import java.util.UUID;

public record OrderView(
    UUID id,
    UUID priceId,
    String planCode,
    String planName,
    BillingCycle cycle,
    String currency,
    long amountVnd,
    long receivedVnd,
    String status,
    String paymentCode,
    Instant createdAt,
    Instant expiresAt,
    Instant termStartsAt,
    Instant termEndsAt,
    String reviewReason) {}
