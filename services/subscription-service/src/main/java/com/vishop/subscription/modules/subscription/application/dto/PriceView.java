package com.vishop.subscription.modules.subscription.application.dto;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import java.util.UUID;

public record PriceView(
    UUID id,
    String planCode,
    String name,
    BillingCycle cycle,
    String currency,
    long amountVnd,
    int shopLimit,
    long monthlyOrders,
    int additionalMembers,
    boolean active) {}
