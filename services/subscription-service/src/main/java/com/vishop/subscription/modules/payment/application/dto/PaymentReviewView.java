package com.vishop.subscription.modules.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentReviewView(
    long id,
    UUID orderId,
    String bank,
    long amountVnd,
    String status,
    String reason,
    Instant occurredAt) {}
