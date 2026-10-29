package com.vishop.subscription.modules.subscription.domain.entity;

import io.github.coreplatform.common.domain.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;

public record PurchaseOrder(
    UUID id,
    UUID tenantId,
    UUID actorId,
    PlanPrice price,
    String paymentCode,
    String idempotencyKey,
    long expectedRevision,
    long receivedVnd,
    State state,
    Instant createdAt,
    Instant expiresAt,
    Instant termStartsAt,
    Instant termEndsAt,
    String reviewReason) {
  public enum State {
    AWAITING_PAYMENT,
    PARTIALLY_PAID,
    FULFILLED,
    EXPIRED,
    CANCELLED,
    REQUIRES_REVIEW
  }

  public boolean open() {
    return state == State.AWAITING_PAYMENT || state == State.PARTIALLY_PAID;
  }

  public PurchaseOrder receive(long amount, Instant now) {
    long received = Math.addExact(receivedVnd, amount);
    String reason =
        !open()
            ? "PAYMENT_FOR_CLOSED_ORDER"
            : !now.isBefore(expiresAt)
                ? "LATE_PAYMENT"
                : received > price.amountVnd() ? "OVERPAYMENT" : null;
    State next =
        reason != null
            ? State.REQUIRES_REVIEW
            : received == price.amountVnd() ? State.FULFILLED : State.PARTIALLY_PAID;
    return copy(received, next, termStartsAt, termEndsAt, reason);
  }

  public PurchaseOrder fulfilled(Instant start, Instant end) {
    return copy(receivedVnd, State.FULFILLED, start, end, null);
  }

  public PurchaseOrder review(String reason) {
    return copy(receivedVnd, State.REQUIRES_REVIEW, termStartsAt, termEndsAt, reason);
  }

  public PurchaseOrder close(Instant now, boolean cancel) {
    if (!open()) throw new ConflictException("Order is already closed");
    if (receivedVnd > 0) return review("CLOSED_WITH_PARTIAL_PAYMENT");
    return copy(receivedVnd, cancel ? State.CANCELLED : State.EXPIRED, null, null, null);
  }

  private PurchaseOrder copy(long received, State next, Instant start, Instant end, String reason) {
    return new PurchaseOrder(
        id,
        tenantId,
        actorId,
        price,
        paymentCode,
        idempotencyKey,
        expectedRevision,
        received,
        next,
        createdAt,
        expiresAt,
        start,
        end,
        reason);
  }
}
