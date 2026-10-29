package com.vishop.subscription.modules.subscription.domain.port;

import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.entity.Subscription;
import java.util.*;

public interface SubscriptionRepository {
  record OrderReference(UUID id, UUID tenantId) {}

  Subscription lockAccount(UUID tenantId);

  Subscription subscription(UUID tenantId);

  void save(Subscription account);

  PlanPrice price(UUID id);

  void save(PlanPrice price);

  Optional<PurchaseOrder> order(UUID id);

  Optional<OrderReference> byPaymentCode(String code);

  Optional<PurchaseOrder> byIdempotency(UUID tenant, UUID actor, String key);

  Optional<PurchaseOrder> openOrder(UUID tenant);

  PurchaseOrder lockOrder(UUID id);

  void save(PurchaseOrder order);

  void saveTerm(PurchaseOrder order);
}
