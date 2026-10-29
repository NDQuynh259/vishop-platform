package com.vishop.subscription.modules.subscription.application.dto;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionView(
    UUID tenantId, String planCode, BillingCycle cycle, String status, Instant endsAt) {}
