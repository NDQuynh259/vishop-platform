package com.vishop.subscription.domain;

import static org.assertj.core.api.Assertions.*;

import com.vishop.subscription.modules.subscription.domain.entity.*;
import com.vishop.subscription.modules.subscription.domain.valueobject.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseOrderTest {
  @Test
  void laterMoneyDoesNotUndoAnAlreadyAppliedTerm() {
    Instant start = Instant.parse("2026-01-31T03:00:00Z"),
        end = Instant.parse("2026-02-28T03:00:00Z");
    var price =
        new PlanPrice(
            UUID.randomUUID(), "BASIC", "Basic", BillingCycle.MONTHLY, 100000, 3, 3000, 3, true);
    var order =
        new PurchaseOrder(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            price,
            "VSTEST",
            "key",
            0,
            0,
            OrderStatus.OPEN,
            FulfillmentStatus.PENDING,
            start,
            start.plusSeconds(1800),
            null,
            null,
            null);
    order = order.receive(40000, start);
    assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
    assertThat(order.readyToFulfill()).isFalse();
    order = order.receive(60000, start);
    assertThat(order.readyToFulfill()).isTrue();
    order = order.fulfilled(start, end).receive(10000, start);
    assertThat(order.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.OVERPAID);
    assertThat(order.fulfillmentStatus()).isEqualTo(FulfillmentStatus.APPLIED);
    assertThat(order.termEndsAt()).isEqualTo(end);
    assertThat(order.needsReview()).isTrue();
    assertThat(order.readyToFulfill()).isFalse();
  }
}
