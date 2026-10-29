package com.vishop.subscription.modules.payment.application.port;

import com.vishop.subscription.modules.payment.application.dto.*;
import io.github.coreplatform.common.domain.pagination.*;

public interface PaymentReadPort {
  PagedResult<PaymentReviewView> reviewPayments(OffsetPagination pagination);

  PagedResult<InboxReviewView> reviewWebhooks(OffsetPagination pagination);
}
