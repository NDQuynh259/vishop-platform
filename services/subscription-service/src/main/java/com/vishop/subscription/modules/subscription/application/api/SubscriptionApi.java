package com.vishop.subscription.modules.subscription.application.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Public in-process boundary. Payment application shares the caller's database transaction. */
public interface SubscriptionApi {
  record OrderReference(UUID id, UUID tenantId) {}

  record PaymentOutcome(UUID orderId, UUID tenantId, boolean review, String reviewReason) {}

  Optional<OrderReference> findOrder(String paymentCode);

  PaymentOutcome applyPayment(UUID orderId, UUID tenantId, long amountVnd, Instant occurredAt);
}
