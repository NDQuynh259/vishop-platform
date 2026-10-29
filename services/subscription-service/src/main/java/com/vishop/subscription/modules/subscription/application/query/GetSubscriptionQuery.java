package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.SubscriptionView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;

public record GetSubscriptionQuery(AuthenticatedActor actor) implements Query<SubscriptionView> {}
