package com.vishop.subscription.modules.payment.infrastructure.persistence.adapters;

import com.vishop.subscription.modules.payment.application.port.WebhookInboxPort;
import com.vishop.subscription.modules.payment.domain.entity.BankTransfer;
import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.PaymentAllocationJpaEntity;
import com.vishop.subscription.modules.payment.infrastructure.persistence.entities.PaymentTransactionJpaEntity;
import com.vishop.subscription.modules.payment.infrastructure.persistence.repositories.SpringDataInboxRepository;
import com.vishop.subscription.modules.payment.infrastructure.persistence.repositories.SpringDataPaymentAllocationRepository;
import com.vishop.subscription.modules.payment.infrastructure.persistence.repositories.SpringDataPaymentTransactionRepository;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.ConflictException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class SePayInboxJpaAdapter implements WebhookInboxPort {
  private final SpringDataInboxRepository inbox;
  private final SpringDataPaymentTransactionRepository payments;
  private final Clock clock;
  private final SpringDataPaymentAllocationRepository allocations;

  public SePayInboxJpaAdapter(
      SpringDataInboxRepository inbox,
      SpringDataPaymentTransactionRepository payments,
      Clock clock,
      SpringDataPaymentAllocationRepository allocations) {
    this.inbox = inbox;
    this.allocations = allocations;
    this.payments = payments;
    this.clock = clock;
  }

  public void receive(long id, String raw, String hash, Instant now) {
    if (inbox.receive(id, raw, hash, now) == 0
        && !inbox.findById(id).orElseThrow().getPayloadHash().equals(hash))
      throw new ConflictException("SePay transaction id already exists with another payload");
  }

  public Optional<Message> lock(long id) {
    return inbox
        .lock(id)
        .map(e -> new Message(e.getId(), e.getRawPayload(), e.getStatus(), e.getReceivedAt()));
  }

  public void complete(long id) {
    var e = inbox.findById(id).orElseThrow();
    if ("PENDING".equals(e.getStatus())) {
      e.setStatus("DONE");
      e.setProcessedAt(clock.instant());
      inbox.save(e);
    }
  }

  public void conflict(long id, String reason) {
    var e = inbox.findById(id).orElseThrow();
    e.setStatus("REVIEW");
    e.setReviewReason(reason);
    inbox.save(e);
  }

  public boolean duplicateReference(BankTransfer t) {
    if (t.referenceCode() == null || t.referenceCode().isBlank()) return false;
    var found =
        payments.findByBankIgnoreCaseAndAccountNumberAndReferenceCode(
            t.bank(), t.accountNumber(), t.referenceCode());
    if (found.isEmpty()) return false;
    var message = inbox.findById(t.id()).orElseThrow();
    message.setPaymentTransactionId(found.get().getId());
    inbox.save(message);
    if (found.get().getAmountVnd() != t.amountVnd()
        || !found.get().getTransferType().equals(t.transferType()))
      conflict(t.id(), "CONFLICTING_BANK_REFERENCE");
    return true;
  }

  public void record(BankTransfer t, UUID order, UUID tenant, String status, String reason) {
    var e = new PaymentTransactionJpaEntity();
    e.setId(UuidV7.nextId());
    e.setProviderTransactionId(t.id());
    e.setMatchedOrderId(order);
    e.setMatchedTenantId(tenant);
    e.setAccountNumber(t.accountNumber());
    e.setBank(t.bank());
    e.setReferenceCode(t.referenceCode());
    e.setSubAccount(t.subAccount());
    e.setPaymentCode(t.code());
    e.setAmountVnd(t.amountVnd());
    e.setTransferType(t.transferType());
    e.setStatus(status);
    e.setReviewReason(reason);
    e.setOccurredAt(t.occurredAt());
    e.setCreatedAt(clock.instant());
    payments.saveAndFlush(e);
    var message = inbox.findById(t.id()).orElseThrow();
    message.setPaymentTransactionId(e.getId());
    inbox.save(message);
    if ("ALLOCATED".equals(status)) {
      var allocation = new PaymentAllocationJpaEntity();
      allocation.setId(UuidV7.nextId());
      allocation.setPaymentId(e.getId());
      allocation.setTenantId(tenant);
      allocation.setOrderId(order);
      allocation.setAmountVnd(t.amountVnd());
      allocation.setCreatedAt(clock.instant());
      allocations.saveAndFlush(allocation);
    }
  }

  public List<Long> pending(int limit) {
    return inbox.pending(PageRequest.of(0, limit));
  }
}
