package com.vishop.subscription.modules.subscription.domain.entity;

import com.vishop.subscription.modules.subscription.domain.valueobject.*;
import io.github.coreplatform.common.domain.exception.*;
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
    OrderStatus orderStatus,
    FulfillmentStatus fulfillmentStatus,
    Instant createdAt,
    Instant expiresAt,
    Instant termStartsAt,
    Instant termEndsAt,
    String reviewReason) {
  /** Existing HTTP status names remain available as a derived compatibility projection. */
  public enum State {
    AWAITING_PAYMENT,
    PARTIALLY_PAID,
    FULFILLED,
    EXPIRED,
    CANCELLED,
    REQUIRES_REVIEW
  }

  public PaymentStatus paymentStatus() {
    return receivedVnd == 0
        ? PaymentStatus.UNPAID
        : receivedVnd < price.amountVnd()
            ? PaymentStatus.PARTIAL
            : receivedVnd == price.amountVnd() ? PaymentStatus.PAID : PaymentStatus.OVERPAID;
  }

  public boolean needsReview() {
    return reviewReason != null || fulfillmentStatus == FulfillmentStatus.REVIEW;
  }

  public State state() {
    if (needsReview()) return State.REQUIRES_REVIEW;
    if (fulfillmentStatus == FulfillmentStatus.APPLIED) return State.FULFILLED;
    if (orderStatus == OrderStatus.EXPIRED) return State.EXPIRED;
    if (orderStatus == OrderStatus.CANCELLED) return State.CANCELLED;
    return receivedVnd > 0 ? State.PARTIALLY_PAID : State.AWAITING_PAYMENT;
  }

  public boolean open() {
    return orderStatus == OrderStatus.OPEN && fulfillmentStatus == FulfillmentStatus.PENDING;
  }

  public boolean readyToFulfill() {
    return open() && !needsReview() && paymentStatus() == PaymentStatus.PAID;
  }

  public PurchaseOrder receive(long amount, Instant occurredAt) {
    if (amount <= 0) throw new BadRequestException("Payment amount must be positive");
    long received = Math.addExact(receivedVnd, amount);
    String reason =
        !open()
            ? "PAYMENT_FOR_CLOSED_ORDER"
            : !occurredAt.isBefore(expiresAt)
                ? "LATE_PAYMENT"
                : received > price.amountVnd() ? "OVERPAYMENT" : null;
    FulfillmentStatus fulfillment =
        reason == null
            ? fulfillmentStatus
            : fulfillmentStatus == FulfillmentStatus.APPLIED
                ? FulfillmentStatus.APPLIED
                : FulfillmentStatus.REVIEW;
    return copy(received, orderStatus, fulfillment, termStartsAt, termEndsAt, reason);
  }

  public PurchaseOrder fulfilled(Instant start, Instant end) {
    return copy(receivedVnd, OrderStatus.COMPLETED, FulfillmentStatus.APPLIED, start, end, null);
  }

  public PurchaseOrder review(String reason) {
    return copy(
        receivedVnd,
        orderStatus,
        fulfillmentStatus == FulfillmentStatus.APPLIED
            ? FulfillmentStatus.APPLIED
            : FulfillmentStatus.REVIEW,
        termStartsAt,
        termEndsAt,
        reason);
  }

  public PurchaseOrder close(Instant now, boolean cancel) {
    if (!open()) throw new ConflictException("Order is already closed");
    return copy(
        receivedVnd,
        cancel ? OrderStatus.CANCELLED : OrderStatus.EXPIRED,
        receivedVnd > 0 ? FulfillmentStatus.REVIEW : FulfillmentStatus.PENDING,
        null,
        null,
        receivedVnd > 0 ? "CLOSED_WITH_PARTIAL_PAYMENT" : null);
  }

  private PurchaseOrder copy(
      long received,
      OrderStatus lifecycle,
      FulfillmentStatus fulfillment,
      Instant start,
      Instant end,
      String reason) {
    return new PurchaseOrder(
        id,
        tenantId,
        actorId,
        price,
        paymentCode,
        idempotencyKey,
        expectedRevision,
        received,
        lifecycle,
        fulfillment,
        createdAt,
        expiresAt,
        start,
        end,
        reason);
  }
}
