package com.vishop.subscription.modules.payment.infrastructure.persistence.adapters;

import com.vishop.subscription.modules.payment.application.dto.*;
import com.vishop.subscription.modules.payment.application.port.PaymentReadPort;
import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.*;
import io.github.coreplatform.common.domain.pagination.*;
import io.github.coreplatform.common.persistence.pagination.JpaListPagination;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentReadJpaAdapter implements PaymentReadPort {
  private final EntityManager entityManager;

  public PaymentReadJpaAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public PagedResult<PaymentReviewView> reviewPayments(ListPagination pagination) {
    return JpaListPagination.find(
        entityManager,
        PaymentTransactionJpaEntity.class,
        (root, query, cb) -> cb.equal(root.get("status"), "REVIEW"),
        Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        pagination,
        "payment-review",
        e ->
            new PaymentReviewView(
                e.getProviderTransactionId(),
                e.getId(),
                e.getAllocatedVnd(),
                e.getMatchedOrderId(),
                e.getBank(),
                e.getAmountVnd(),
                e.getStatus(),
                e.getReviewReason(),
                e.getOccurredAt()));
  }

  public PagedResult<InboxReviewView> reviewWebhooks(ListPagination pagination) {
    return JpaListPagination.find(
        entityManager,
        SePayInboxJpaEntity.class,
        (root, query, cb) -> cb.equal(root.get("status"), "REVIEW"),
        Sort.by(Sort.Direction.DESC, "receivedAt", "id"),
        pagination,
        "webhook-review",
        e -> new InboxReviewView(e.getId(), e.getStatus(), e.getReviewReason(), e.getReceivedAt()));
  }
}
