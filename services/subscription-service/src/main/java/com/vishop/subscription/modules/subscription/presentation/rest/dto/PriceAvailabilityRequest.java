package com.vishop.subscription.modules.subscription.presentation.rest.dto;

import jakarta.validation.constraints.NotNull;

public record PriceAvailabilityRequest(@NotNull Boolean active) {}
