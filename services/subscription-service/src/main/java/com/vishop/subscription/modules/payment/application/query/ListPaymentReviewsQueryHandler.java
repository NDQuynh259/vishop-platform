package com.vishop.subscription.modules.payment.application.query;

import com.vishop.subscription.modules.payment.application.dto.PaymentReviewView;
import com.vishop.subscription.modules.payment.application.port.PaymentReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListPaymentReviewsQueryHandler
    implements QueryHandler<ListPaymentReviewsQuery, PagedResult<PaymentReviewView>> {
  private final PaymentReadPort reads;

  public ListPaymentReviewsQueryHandler(PaymentReadPort reads) {
    this.reads = reads;
  }

  @Transactional(readOnly = true)
  public PagedResult<PaymentReviewView> handle(ListPaymentReviewsQuery q) {
    q.actor().requireCms();
    return reads.reviewPayments(q.pagination());
  }
}
