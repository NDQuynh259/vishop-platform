package com.vishop.subscription.modules.subscription.presentation.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCheckoutRequest(@NotNull UUID priceId) {}
