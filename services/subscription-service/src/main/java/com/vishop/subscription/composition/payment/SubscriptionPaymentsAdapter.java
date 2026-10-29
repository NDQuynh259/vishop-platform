package com.vishop.subscription.composition.payment;

import com.vishop.subscription.modules.payment.application.api.SubscriptionPaymentsPort;
import com.vishop.subscription.modules.subscription.application.api.SubscriptionApi;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPaymentsAdapter implements SubscriptionPaymentsPort {
  private final SubscriptionApi subscriptions;

  public SubscriptionPaymentsAdapter(SubscriptionApi subscriptions) {
    this.subscriptions = subscriptions;
  }

  public Optional<OrderReference> findOrder(String code) {
    return subscriptions.findOrder(code).map(r -> new OrderReference(r.id(), r.tenantId()));
  }

  public PaymentOutcome applyPayment(UUID order, UUID tenant, long amount, Instant occurred) {
    var outcome = subscriptions.applyPayment(order, tenant, amount, occurred);
    return new PaymentOutcome(
        outcome.orderId(), outcome.tenantId(), outcome.review(), outcome.reviewReason());
  }
}
