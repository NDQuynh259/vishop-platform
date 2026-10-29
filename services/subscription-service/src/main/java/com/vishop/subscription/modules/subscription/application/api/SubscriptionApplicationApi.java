package com.vishop.subscription.modules.subscription.application.api;

import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
class SubscriptionApplicationApi implements SubscriptionApi {
  private final SubscriptionRepository data;
  private final BusinessEventPort events;
  private final Clock clock;

  public SubscriptionApplicationApi(
      SubscriptionRepository data, BusinessEventPort events, Clock clock) {
    this.data = data;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Optional<OrderReference> findOrder(String code) {
    return data.byPaymentCode(code).map(r -> new OrderReference(r.id(), r.tenantId()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PaymentOutcome applyPayment(
      UUID orderId, UUID tenantId, long amountVnd, Instant occurredAt) {
    var account = data.lockAccount(tenantId);
    var order = data.lockOrder(orderId);
    if (!tenantId.equals(order.tenantId())) throw new EntityNotFoundException("Order", orderId);
    boolean previouslyFulfilled = order.termEndsAt() != null;
    order = order.receive(amountVnd, occurredAt);
    if (order.state() == PurchaseOrder.State.FULFILLED && !previouslyFulfilled) {
      if (account.revision() != order.expectedRevision())
        order = order.review("SUBSCRIPTION_CHANGED");
      else {
        Instant now = clock.instant();
        var start = account.activeAt(now) ? account.endsAt() : now;
        var updated = account.purchase(order.price(), now);
        order = order.fulfilled(start, updated.endsAt());
        data.save(updated);
        data.save(order);
        data.saveTerm(order);
        events.append(
            "SubscriptionActivated",
            order.tenantId(),
            order.tenantId(),
            Map.of(
                "planCode",
                updated.planCode(),
                "startsAt",
                start,
                "endsAt",
                updated.endsAt(),
                "orderId",
                order.id(),
                "subscriptionRevision",
                updated.revision(),
                "cycle",
                updated.cycle().name(),
                "shopLimit",
                order.price().shopLimit(),
                "monthlyOrders",
                order.price().monthlyOrders(),
                "additionalMembers",
                order.price().additionalMembers()));
      }
    }
    data.save(order);
    return new PaymentOutcome(
        order.id(),
        order.tenantId(),
        order.state() == PurchaseOrder.State.REQUIRES_REVIEW,
        order.reviewReason());
  }
}
