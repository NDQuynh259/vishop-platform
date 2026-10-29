package com.vishop.subscription.modules.payment.infrastructure.persistence.adapters;

import com.vishop.subscription.modules.payment.application.port.PaymentReadPort;
import com.vishop.subscription.modules.payment.infrastructure.persistence.repositories.*;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.pagination.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentReadJpaAdapter implements PaymentReadPort {
  private final SpringDataBankPaymentRepository payments;
  private final SpringDataInboxRepository inbox;

  public PaymentReadJpaAdapter(
      SpringDataBankPaymentRepository payments, SpringDataInboxRepository inbox) {
    this.payments = payments;
    this.inbox = inbox;
  }

  public PagedResult<com.vishop.subscription.modules.payment.application.dto.PaymentReviewView>
      reviewPayments(OffsetPagination p) {
    if (p.offset() > Integer.MAX_VALUE)
      throw new BadRequestException("Pagination offset is too large");
    var page =
        payments.findByStatus(
            "REVIEW", PageRequest.of(p.pageIndex(), p.limit(), Sort.by("createdAt").descending()));
    return PagedResult.ofOffset(
        page.stream()
            .map(
                e ->
                    new com.vishop.subscription.modules.payment.application.dto.PaymentReviewView(
                        e.getId(),
                        e.getOrderId(),
                        e.getBank(),
                        e.getAmountVnd(),
                        e.getStatus(),
                        e.getReviewReason(),
                        e.getOccurredAt()))
            .toList(),
        p.page(),
        p.limit(),
        page.getTotalElements());
  }

  public PagedResult<com.vishop.subscription.modules.payment.application.dto.InboxReviewView>
      reviewWebhooks(OffsetPagination p) {
    if (p.offset() > Integer.MAX_VALUE)
      throw new BadRequestException("Pagination offset is too large");
    var page =
        inbox.findByStatus(
            "REVIEW", PageRequest.of(p.pageIndex(), p.limit(), Sort.by("receivedAt").descending()));
    return PagedResult.ofOffset(
        page.stream()
            .map(
                e ->
                    new com.vishop.subscription.modules.payment.application.dto.InboxReviewView(
                        e.getId(), e.getStatus(), e.getReviewReason(), e.getReceivedAt()))
            .toList(),
        p.page(),
        p.limit(),
        page.getTotalElements());
  }
}
