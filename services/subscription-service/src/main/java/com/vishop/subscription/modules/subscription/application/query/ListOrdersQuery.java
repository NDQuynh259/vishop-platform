package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.OrderView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;

public record ListOrdersQuery(
    AuthenticatedActor actor,
    io.github.coreplatform.common.domain.pagination.OffsetPagination pagination)
    implements Query<io.github.coreplatform.common.domain.pagination.PagedResult<OrderView>> {}
