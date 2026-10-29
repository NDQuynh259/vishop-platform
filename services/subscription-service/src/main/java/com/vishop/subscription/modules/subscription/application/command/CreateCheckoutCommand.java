package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record CreateCheckoutCommand(AuthenticatedActor actor, UUID priceId, String idempotencyKey)
    implements Command<CheckoutView> {}
