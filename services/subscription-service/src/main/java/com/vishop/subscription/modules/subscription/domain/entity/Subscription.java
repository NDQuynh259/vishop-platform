package com.vishop.subscription.modules.subscription.domain.entity;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.time.*;
import java.util.UUID;

public record Subscription(
    UUID tenantId,
    String planCode,
    BillingCycle cycle,
    Instant anchorAt,
    int purchasedCycles,
    Instant endsAt,
    long revision) {
  public static Subscription free(UUID tenant) {
    return new Subscription(tenant, "FREE", null, null, 0, null, 0);
  }

  public boolean activeAt(Instant now) {
    return endsAt != null && endsAt.isAfter(now);
  }

  public void canPurchase(PlanPrice price, Instant now) {
    if (activeAt(now) && (!price.planCode().equals(planCode) || price.cycle() != cycle))
      throw new ConflictException("Active subscriptions can only renew the same plan and cycle");
  }

  public Subscription purchase(PlanPrice price, Instant now) {
    canPurchase(price, now);
    boolean renewing = activeAt(now);
    Instant anchor = renewing ? anchorAt : now;
    int cycles = renewing ? Math.addExact(purchasedCycles, 1) : 1;
    var local = anchor.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    Instant end =
        (price.cycle() == BillingCycle.MONTHLY ? local.plusMonths(cycles) : local.plusYears(cycles))
            .toInstant();
    return new Subscription(
        tenantId, price.planCode(), price.cycle(), anchor, cycles, end, revision + 1);
  }
}
