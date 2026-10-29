package com.vishop.subscription.modules.payment.application.query;

import com.vishop.subscription.modules.payment.application.dto.InboxReviewView;
import com.vishop.subscription.modules.payment.application.port.PaymentReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListWebhookReviewsQueryHandler
    implements QueryHandler<ListWebhookReviewsQuery, PagedResult<InboxReviewView>> {
  private final PaymentReadPort reads;

  public ListWebhookReviewsQueryHandler(PaymentReadPort reads) {
    this.reads = reads;
  }

  @Transactional(readOnly = true)
  public PagedResult<InboxReviewView> handle(ListWebhookReviewsQuery q) {
    q.actor().requireCms();
    return reads.reviewWebhooks(q.pagination());
  }
}
