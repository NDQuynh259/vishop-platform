package com.vishop.subscription.modules.subscription.application.port;

import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import io.github.coreplatform.common.domain.pagination.*;
import java.util.*;

public interface SubscriptionReadPort {
  List<PlanPrice> prices();

  PagedResult<PurchaseOrder> orders(UUID tenantId, OffsetPagination pagination);
}
