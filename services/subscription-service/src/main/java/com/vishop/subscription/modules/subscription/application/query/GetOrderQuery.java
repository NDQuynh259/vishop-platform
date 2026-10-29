package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetOrderQuery(AuthenticatedActor actor, UUID orderId)
    implements Query<CheckoutView> {}
