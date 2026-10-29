package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.OrderView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.application.service.CheckoutService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, OrderView> {
  private final CheckoutService service;

  public CancelOrderCommandHandler(CheckoutService service) {
    this.service = service;
  }

  public OrderView handle(CancelOrderCommand command) {
    return SubscriptionResponseMapper.order(service.cancel(command.actor(), command.orderId()));
  }
}
