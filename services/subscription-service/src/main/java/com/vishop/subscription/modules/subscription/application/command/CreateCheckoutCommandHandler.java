package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.modules.subscription.application.service.CheckoutService;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class CreateCheckoutCommandHandler
    implements CommandHandler<CreateCheckoutCommand, CheckoutView> {
  private final CheckoutService service;

  public CreateCheckoutCommandHandler(CheckoutService service) {
    this.service = service;
  }

  public CheckoutView handle(CreateCheckoutCommand command) {
    return service.create(command.actor(), command.priceId(), command.idempotencyKey());
  }
}
