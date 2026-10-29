package com.vishop.subscription.modules.subscription.domain.entity;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.util.UUID;

public record PlanPrice(
    UUID id,
    String planCode,
    String name,
    BillingCycle cycle,
    long amountVnd,
    int shopLimit,
    long monthlyOrders,
    int additionalMembers,
    boolean active) {
  public PlanPrice {
    if (id == null
        || planCode == null
        || !planCode.matches("BASIC|PRO|BUSINESS")
        || name == null
        || name.isBlank()
        || name.length() > 200
        || cycle == null
        || amountVnd <= 0
        || amountVnd > 1_000_000_000_000L
        || shopLimit < 1
        || monthlyOrders < 1
        || additionalMembers < 0) throw new BadRequestException("Invalid plan price or limits");
    name = name.trim();
  }
}
