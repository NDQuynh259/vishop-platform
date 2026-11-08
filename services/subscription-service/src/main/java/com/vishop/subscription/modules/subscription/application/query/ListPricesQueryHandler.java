package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.application.port.SubscriptionReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListPricesQueryHandler
    implements QueryHandler<ListPricesQuery, PagedResult<PriceView>> {
  private final SubscriptionReadPort reads;

  public ListPricesQueryHandler(SubscriptionReadPort reads) {
    this.reads = reads;
  }

  @Transactional(readOnly = true)
  public PagedResult<PriceView> handle(ListPricesQuery c) {
    c.actor().require(CommonPermissions.Billing.VIEW);
    var page = reads.prices(c.pagination());
    return page.map(SubscriptionResponseMapper::price);
  }
}
