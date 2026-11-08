package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;

public record ListPricesQuery(AuthenticatedActor actor, ListPagination pagination)
    implements Query<PagedResult<PriceView>> {}
