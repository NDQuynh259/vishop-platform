package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.SubscriptionView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetSubscriptionQueryHandler
    implements QueryHandler<GetSubscriptionQuery, SubscriptionView> {
  private final SubscriptionRepository data;
  private final java.time.Clock clock;

  public GetSubscriptionQueryHandler(SubscriptionRepository data, java.time.Clock clock) {
    this.data = data;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SubscriptionView handle(GetSubscriptionQuery c) {
    c.actor().require(CommonPermissions.Billing.VIEW);
    return SubscriptionResponseMapper.subscription(
        data.subscription(c.actor().tenantId()), clock.instant());
  }
}
