package com.vishop.subscription.modules.subscription.infrastructure.persistence.mapper;

import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.entity.Subscription;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PlanPriceJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PurchaseOrderJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionAccountJpaEntity;

public final class SubscriptionPersistenceMapper {
  private SubscriptionPersistenceMapper() {}

  public static PlanPrice domain(PlanPriceJpaEntity e) {
    return new PlanPrice(
        e.getId(),
        e.getPlanCode(),
        e.getName(),
        e.getCycle(),
        e.getAmountVnd(),
        e.getShopLimit(),
        e.getMonthlyOrders(),
        e.getAdditionalMembers(),
        e.isActive());
  }

  public static Subscription domain(SubscriptionAccountJpaEntity e) {
    return new Subscription(
        e.getTenantId(),
        e.getPlanCode(),
        e.getCycle(),
        e.getAnchorAt(),
        e.getPurchasedCycles(),
        e.getEndsAt(),
        e.getRevision());
  }

  public static PurchaseOrder domain(PurchaseOrderJpaEntity e) {
    return new PurchaseOrder(
        e.getId(),
        e.getTenantId(),
        e.getActorId(),
        new PlanPrice(
            e.getPriceId(),
            e.getPlanCode(),
            e.getPlanName(),
            e.getCycle(),
            e.getAmountVnd(),
            e.getShopLimit(),
            e.getMonthlyOrders(),
            e.getAdditionalMembers(),
            true),
        e.getPaymentCode(),
        e.getIdempotencyKey(),
        e.getExpectedRevision(),
        e.getReceivedVnd(),
        e.getState(),
        e.getCreatedAt(),
        e.getExpiresAt(),
        e.getTermStartsAt(),
        e.getTermEndsAt(),
        e.getReviewReason());
  }

  public static void copy(PlanPrice d, PlanPriceJpaEntity e) {
    e.setId(d.id());
    e.setPlanCode(d.planCode());
    e.setName(d.name());
    e.setCycle(d.cycle());
    e.setAmountVnd(d.amountVnd());
    e.setShopLimit(d.shopLimit());
    e.setMonthlyOrders(d.monthlyOrders());
    e.setAdditionalMembers(d.additionalMembers());
    e.setActive(d.active());
  }

  public static void copy(Subscription d, SubscriptionAccountJpaEntity e) {
    e.setTenantId(d.tenantId());
    e.setPlanCode(d.planCode());
    e.setCycle(d.cycle());
    e.setAnchorAt(d.anchorAt());
    e.setPurchasedCycles(d.purchasedCycles());
    e.setEndsAt(d.endsAt());
    e.setRevision(d.revision());
  }

  public static void copy(PurchaseOrder d, PurchaseOrderJpaEntity e) {
    e.setId(d.id());
    e.setTenantId(d.tenantId());
    e.setActorId(d.actorId());
    e.setPriceId(d.price().id());
    e.setPlanCode(d.price().planCode());
    e.setPlanName(d.price().name());
    e.setCycle(d.price().cycle());
    e.setAmountVnd(d.price().amountVnd());
    e.setShopLimit(d.price().shopLimit());
    e.setMonthlyOrders(d.price().monthlyOrders());
    e.setAdditionalMembers(d.price().additionalMembers());
    e.setPaymentCode(d.paymentCode());
    e.setIdempotencyKey(d.idempotencyKey());
    e.setExpectedRevision(d.expectedRevision());
    e.setReceivedVnd(d.receivedVnd());
    e.setState(d.state());
    e.setCreatedAt(d.createdAt());
    e.setExpiresAt(d.expiresAt());
    e.setTermStartsAt(d.termStartsAt());
    e.setTermEndsAt(d.termEndsAt());
    e.setReviewReason(d.reviewReason());
  }
}
