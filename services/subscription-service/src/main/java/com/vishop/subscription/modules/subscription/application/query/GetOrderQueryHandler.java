package com.vishop.subscription.modules.subscription.application.query;

import com.vishop.subscription.modules.subscription.application.api.CheckoutPaymentPort;
import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.modules.subscription.application.service.CheckoutService;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, CheckoutView> {
  private final SubscriptionRepository data;
  private final CheckoutPaymentPort payments;

  public GetOrderQueryHandler(SubscriptionRepository data, CheckoutPaymentPort payments) {
    this.data = data;
    this.payments = payments;
  }

  @Transactional(readOnly = true)
  public CheckoutView handle(GetOrderQuery c) {
    c.actor().require(CommonPermissions.Billing.VIEW);
    var order =
        data.order(c.orderId())
            .orElseThrow(() -> new EntityNotFoundException("Order", c.orderId()));
    CheckoutService.requireTenant(c.actor(), order);
    return checkout(order);
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
