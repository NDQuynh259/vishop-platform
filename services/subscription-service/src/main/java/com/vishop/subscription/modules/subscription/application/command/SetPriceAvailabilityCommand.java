package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record SetPriceAvailabilityCommand(AuthenticatedActor actor, UUID priceId, boolean active)
    implements Command<PriceView> {}
