package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;

public record ListPricesQuery(AuthenticatedActor actor)
    implements Query<java.util.List<PriceView>> {}
