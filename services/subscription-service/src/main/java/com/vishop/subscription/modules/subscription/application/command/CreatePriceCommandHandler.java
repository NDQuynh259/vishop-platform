package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreatePriceCommandHandler implements CommandHandler<CreatePriceCommand, PriceView> {
  private final SubscriptionRepository data;
  private final BusinessEventPort events;

  public CreatePriceCommandHandler(SubscriptionRepository data, BusinessEventPort events) {
    this.data = data;
    this.events = events;
  }

  @Transactional
  public PriceView handle(CreatePriceCommand c) {
    c.actor().requireCms();
    var price =
        new PlanPrice(
            UuidV7.nextId(),
            c.planCode(),
            c.name(),
            c.cycle(),
            c.amountVnd(),
            c.shopLimit(),
            c.monthlyOrders(),
            c.additionalMembers(),
            true);
    data.save(price);
    events.append(
        "BillingPricePublished",
        price.id(),
        null,
        Map.of(
            "actorId",
            c.actor().userId(),
            "planCode",
            price.planCode(),
            "amountVnd",
            price.amountVnd()));
    return SubscriptionResponseMapper.price(price);
  }
}
