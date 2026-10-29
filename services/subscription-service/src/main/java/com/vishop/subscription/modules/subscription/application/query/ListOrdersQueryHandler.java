package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.OrderView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.application.port.SubscriptionReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListOrdersQueryHandler
    implements QueryHandler<
        ListOrdersQuery, io.github.coreplatform.common.domain.pagination.PagedResult<OrderView>> {
  private final SubscriptionReadPort reads;

  public ListOrdersQueryHandler(SubscriptionReadPort reads) {
    this.reads = reads;
  }

  @Transactional(readOnly = true)
  public io.github.coreplatform.common.domain.pagination.PagedResult<OrderView> handle(
      ListOrdersQuery c) {
    c.actor().require(CommonPermissions.Billing.VIEW);
    var page = reads.orders(c.actor().tenantId(), c.pagination());
    return io.github.coreplatform.common.domain.pagination.PagedResult.ofOffset(
        page.items().stream().map(SubscriptionResponseMapper::order).toList(),
        page.page(),
        page.limit(),
        page.total());
  }
}
