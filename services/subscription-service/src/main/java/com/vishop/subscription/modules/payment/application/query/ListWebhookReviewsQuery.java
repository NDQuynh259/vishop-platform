package com.vishop.subscription.modules.payment.application.query;

import com.vishop.subscription.modules.payment.application.dto.InboxReviewView;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.*;

public record ListWebhookReviewsQuery(AuthenticatedActor actor, OffsetPagination pagination)
    implements Query<PagedResult<InboxReviewView>> {}
