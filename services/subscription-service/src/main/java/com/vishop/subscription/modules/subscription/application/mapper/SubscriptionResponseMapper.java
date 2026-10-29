package com.vishop.subscription.modules.subscription.application.mapper;

import com.vishop.subscription.modules.subscription.application.dto.OrderView;
import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.application.dto.SubscriptionView;
import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.entity.Subscription;
import java.time.Instant;

public final class SubscriptionResponseMapper {
  private SubscriptionResponseMapper() {}

  public static PriceView price(PlanPrice p) {
    return new PriceView(
        p.id(),
        p.planCode(),
        p.name(),
        p.cycle(),
        "VND",
        p.amountVnd(),
        p.shopLimit(),
        p.monthlyOrders(),
        p.additionalMembers(),
        p.active());
  }

  public static OrderView order(PurchaseOrder o) {
    return new OrderView(
        o.id(),
        o.price().id(),
        o.price().planCode(),
        o.price().name(),
        o.price().cycle(),
        "VND",
        o.price().amountVnd(),
        o.receivedVnd(),
        o.state().name(),
        o.paymentCode(),
        o.createdAt(),
        o.expiresAt(),
        o.termStartsAt(),
        o.termEndsAt(),
        o.reviewReason());
  }

  public static SubscriptionView subscription(Subscription s, Instant now) {
    return new SubscriptionView(
        s.tenantId(),
        s.planCode(),
        s.cycle(),
        s.endsAt() == null ? "FREE" : s.activeAt(now) ? "ACTIVE" : "EXPIRED",
        s.endsAt());
  }
}
