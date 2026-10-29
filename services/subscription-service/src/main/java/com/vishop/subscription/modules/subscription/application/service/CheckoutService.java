package com.vishop.subscription.modules.subscription.application.service;

import com.vishop.subscription.modules.subscription.application.api.CheckoutPaymentPort;
import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.*;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
  private final SubscriptionRepository data;
  private final CheckoutPaymentPort payments;
  private final BusinessEventPort events;
  private final Clock clock;
  private final Duration lifetime;

  public CheckoutService(
      SubscriptionRepository data,
      CheckoutPaymentPort payments,
      BusinessEventPort events,
      Clock clock,
      @Value("${vishop.subscription.checkout-lifetime:PT30M}") Duration lifetime) {
    this.data = data;
    this.payments = payments;
    this.events = events;
    this.clock = clock;
    this.lifetime = lifetime;
    if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(24)) > 0)
      throw new IllegalArgumentException("Invalid checkout lifetime");
  }

  @Transactional
  public CheckoutView create(AuthenticatedActor actor, UUID priceId, String key) {
    actor.require(CommonPermissions.Billing.MANAGE);
    payments.requireConfigured();
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,100}"))
      throw new BadRequestException("Invalid Idempotency-Key");
    var account = data.lockAccount(actor.tenantId());
    var existing = data.byIdempotency(actor.tenantId(), actor.userId(), key);
    if (existing.isPresent()) {
      if (!existing.get().price().id().equals(priceId))
        throw new ConflictException("Idempotency-Key was used with a different request");
      return checkout(existing.get());
    }
    Instant now = clock.instant();
    data.openOrder(actor.tenantId())
        .ifPresent(
            open -> {
              if (now.isBefore(open.expiresAt()))
                throw new ConflictException("A subscription order is already awaiting payment");
              data.save(open.close(now, false));
            });
    var price = data.price(priceId);
    if (!price.active()) throw new BadRequestException("Price is not available");
    account.canPurchase(price, now);
    var order =
        new PurchaseOrder(
            UuidV7.nextId(),
            actor.tenantId(),
            actor.userId(),
            price,
            "VS" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
            key,
            account.revision(),
            0,
            PurchaseOrder.State.AWAITING_PAYMENT,
            now,
            now.plus(lifetime),
            null,
            null,
            null);
    data.save(order);
    events.append(
        "BillingOrderCreated",
        order.id(),
        order.tenantId(),
        Map.of("actorId", actor.userId(), "priceId", price.id(), "amountVnd", price.amountVnd()));
    return checkout(order);
  }

  @Transactional
  public PurchaseOrder cancel(AuthenticatedActor actor, UUID id) {
    actor.require(CommonPermissions.Billing.MANAGE);
    data.lockAccount(actor.tenantId());
    var order = data.lockOrder(id);
    requireTenant(actor, order);
    order = order.close(clock.instant(), true);
    data.save(order);
    events.append("BillingOrderCancelled", id, actor.tenantId(), Map.of("actorId", actor.userId()));
    return order;
  }

  public static void requireTenant(AuthenticatedActor actor, PurchaseOrder order) {
    if (!actor.tenantId().equals(order.tenantId()))
      throw new EntityNotFoundException("Order", order.id());
  }

  private com.vishop.subscription.modules.subscription.application.dto.CheckoutView checkout(
      com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder order) {
    var p =
        payments.instructions(
            new CheckoutPaymentPort.Request(
                order.paymentCode(),
                order.price().amountVnd() - order.receivedVnd(),
                order.open(),
                order.expiresAt()));
    return new com.vishop.subscription.modules.subscription.application.dto.CheckoutView(
        com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper
            .order(order),
        p.qrUrl(),
        p.bank(),
        p.accountNumber(),
        p.accountHolder(),
        p.transferMemo());
  }
}
