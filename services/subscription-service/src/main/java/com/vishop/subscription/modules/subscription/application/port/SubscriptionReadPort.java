package com.vishop.subscription.modules.subscription.application.port;

import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import io.github.coreplatform.common.domain.pagination.*;
import java.util.*;

public interface SubscriptionReadPort {
  PagedResult<PlanPrice> prices(ListPagination pagination);

  PagedResult<PurchaseOrder> orders(UUID tenantId, ListPagination pagination);
}
