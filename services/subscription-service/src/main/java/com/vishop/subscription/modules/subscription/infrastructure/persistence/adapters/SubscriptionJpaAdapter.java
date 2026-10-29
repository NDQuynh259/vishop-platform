package com.vishop.subscription.modules.subscription.infrastructure.persistence.adapters;

import com.vishop.subscription.modules.subscription.application.port.SubscriptionReadPort;
import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.entity.PurchaseOrder;
import com.vishop.subscription.modules.subscription.domain.entity.Subscription;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PlanPriceJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.PurchaseOrderJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.entities.SubscriptionTermJpaEntity;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.mapper.SubscriptionPersistenceMapper;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories.SpringDataOrderRepository;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories.SpringDataPriceRepository;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories.SpringDataSubscriptionAccountRepository;
import com.vishop.subscription.modules.subscription.infrastructure.persistence.repositories.SpringDataTermRepository;
import io.github.coreplatform.common.domain.exception.*;
import io.github.coreplatform.common.domain.pagination.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class SubscriptionJpaAdapter implements SubscriptionRepository, SubscriptionReadPort {
  private final SpringDataPriceRepository prices;
  private final SpringDataOrderRepository orders;
  private final SpringDataSubscriptionAccountRepository accounts;
  private final SpringDataTermRepository terms;

  public SubscriptionJpaAdapter(
      SpringDataPriceRepository prices,
      SpringDataOrderRepository orders,
      SpringDataSubscriptionAccountRepository accounts,
      SpringDataTermRepository terms) {
    this.prices = prices;
    this.orders = orders;
    this.accounts = accounts;
    this.terms = terms;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Subscription lockAccount(UUID tenant) {
    accounts.ensure(tenant);
    return SubscriptionPersistenceMapper.domain(accounts.lock(tenant).orElseThrow());
  }

  public Subscription subscription(UUID tenant) {
    return accounts
        .findById(tenant)
        .map(SubscriptionPersistenceMapper::domain)
        .orElse(Subscription.free(tenant));
  }

  public void save(Subscription d) {
    var e = accounts.findById(d.tenantId()).orElseThrow();
    SubscriptionPersistenceMapper.copy(d, e);
    accounts.saveAndFlush(e);
  }

  public PlanPrice price(UUID id) {
    return prices
        .findById(id)
        .map(SubscriptionPersistenceMapper::domain)
        .orElseThrow(() -> new EntityNotFoundException("Price", id));
  }

  public void save(PlanPrice d) {
    var e = prices.findById(d.id()).orElseGet(PlanPriceJpaEntity::new);
    SubscriptionPersistenceMapper.copy(d, e);
    prices.saveAndFlush(e);
  }

  public List<PlanPrice> prices() {
    return prices.findByActiveTrueOrderByPlanCodeAscAmountVndAsc().stream()
        .map(SubscriptionPersistenceMapper::domain)
        .toList();
  }

  public Optional<PurchaseOrder> order(UUID id) {
    return orders.findById(id).map(SubscriptionPersistenceMapper::domain);
  }

  public Optional<SubscriptionRepository.OrderReference> byPaymentCode(String code) {
    return orders
        .paymentReference(code)
        .map(row -> new SubscriptionRepository.OrderReference(row.getId(), row.getTenantId()));
  }

  public Optional<PurchaseOrder> byIdempotency(UUID tenant, UUID actor, String key) {
    return orders
        .findByTenantIdAndActorIdAndIdempotencyKey(tenant, actor, key)
        .map(SubscriptionPersistenceMapper::domain);
  }

  public Optional<PurchaseOrder> openOrder(UUID tenant) {
    return orders.open(tenant).map(SubscriptionPersistenceMapper::domain);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PurchaseOrder lockOrder(UUID id) {
    return orders
        .lock(id)
        .map(SubscriptionPersistenceMapper::domain)
        .orElseThrow(() -> new EntityNotFoundException("Order", id));
  }

  public void save(PurchaseOrder d) {
    var e = orders.findById(d.id()).orElseGet(PurchaseOrderJpaEntity::new);
    SubscriptionPersistenceMapper.copy(d, e);
    orders.saveAndFlush(e);
  }

  public void saveTerm(PurchaseOrder order) {
    var e = new SubscriptionTermJpaEntity();
    e.setOrderId(order.id());
    e.setTenantId(order.tenantId());
    e.setStartsAt(order.termStartsAt());
    e.setEndsAt(order.termEndsAt());
    terms.saveAndFlush(e);
  }

  public PagedResult<PurchaseOrder> orders(UUID tenant, OffsetPagination pagination) {
    if (pagination.offset() > Integer.MAX_VALUE)
      throw new BadRequestException("Pagination offset is too large");
    var page =
        orders.findByTenantId(
            tenant,
            PageRequest.of(
                pagination.pageIndex(),
                pagination.limit(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    return PagedResult.ofOffset(
        page.stream().map(SubscriptionPersistenceMapper::domain).toList(),
        pagination.page(),
        pagination.limit(),
        page.getTotalElements());
  }
}
