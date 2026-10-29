package com.vishop.subscription.domain;

import static org.assertj.core.api.Assertions.*;

import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.Subscription;
import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionTest {
  PlanPrice price(BillingCycle cycle) {
    return new PlanPrice(UUID.randomUUID(), "BASIC", "Basic", cycle, 100000, 3, 3000, 3, true);
  }

  @Test
  void calendarCyclesRetainOriginalMonthEndAndLeapYearAnchor() {
    var monthly = price(BillingCycle.MONTHLY);
    var start = Instant.parse("2024-01-31T03:00:00Z");
    var a = Subscription.free(UUID.randomUUID()).purchase(monthly, start);
    assertThat(a.endsAt()).isEqualTo("2024-02-29T03:00:00Z");
    var b = a.purchase(monthly, start.plusSeconds(1));
    assertThat(b.endsAt()).isEqualTo("2024-03-31T03:00:00Z");
    var year = price(BillingCycle.YEARLY);
    var leap = Instant.parse("2024-02-29T03:00:00Z");
    var yearly = Subscription.free(UUID.randomUUID()).purchase(year, leap);
    assertThat(yearly.endsAt()).isEqualTo("2025-02-28T03:00:00Z");
    for (int i = 0; i < 3; i++) yearly = yearly.purchase(year, leap.plusSeconds(i + 1));
    assertThat(yearly.endsAt()).isEqualTo("2028-02-29T03:00:00Z");
  }

  @Test
  void activeCycleChangesRequireASeparateQuoteButExpiredSubscriptionsCanRestart() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var subscription =
        Subscription.free(UUID.randomUUID()).purchase(price(BillingCycle.MONTHLY), now);
    assertThatThrownBy(() -> subscription.purchase(price(BillingCycle.YEARLY), now))
        .isInstanceOf(ConflictException.class);
    assertThat(subscription.purchase(price(BillingCycle.YEARLY), subscription.endsAt()).endsAt())
        .isEqualTo("2027-02-01T00:00:00Z");
  }
}
