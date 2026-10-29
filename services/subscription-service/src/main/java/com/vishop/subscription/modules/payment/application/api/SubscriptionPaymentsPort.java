package com.vishop.subscription.modules.payment.application.api;

import java.time.Instant;
import java.util.*;

/** Payment's outbound contract; composition connects it to the subscription module. */
public interface SubscriptionPaymentsPort {
  record OrderReference(UUID id, UUID tenantId) {}

  record PaymentOutcome(UUID orderId, UUID tenantId, boolean review, String reviewReason) {}

  Optional<OrderReference> findOrder(String paymentCode);

  PaymentOutcome applyPayment(UUID orderId, UUID tenantId, long amountVnd, Instant occurredAt);
}
