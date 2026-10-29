package com.vishop.subscription.modules.subscription.presentation.rest.dto;

import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import jakarta.validation.constraints.*;

public record CreatePriceRequest(
    @NotBlank @Pattern(regexp = "BASIC|PRO|BUSINESS") String planCode,
    @NotBlank @Size(max = 200) String name,
    @NotNull BillingCycle cycle,
    @Min(1) @Max(1000000000000L) long amountVnd,
    @Min(1) int shopLimit,
    @Min(1) long monthlyOrders,
    @Min(0) int additionalMembers) {}
