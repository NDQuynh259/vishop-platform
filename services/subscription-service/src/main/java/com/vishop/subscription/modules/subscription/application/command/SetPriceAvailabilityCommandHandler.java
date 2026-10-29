package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.application.mapper.SubscriptionResponseMapper;
import com.vishop.subscription.modules.subscription.domain.entity.PlanPrice;
import com.vishop.subscription.modules.subscription.domain.port.SubscriptionRepository;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SetPriceAvailabilityCommandHandler
    implements CommandHandler<SetPriceAvailabilityCommand, PriceView> {
  private final SubscriptionRepository data;
  private final BusinessEventPort events;

  public SetPriceAvailabilityCommandHandler(SubscriptionRepository data, BusinessEventPort events) {
    this.data = data;
    this.events = events;
  }

  @Transactional
  public PriceView handle(SetPriceAvailabilityCommand c) {
    c.actor().requireCms();
    var p = data.price(c.priceId());
    if (p.active() == c.active()) return SubscriptionResponseMapper.price(p);
    var updated =
        new PlanPrice(
            p.id(),
            p.planCode(),
            p.name(),
            p.cycle(),
            p.amountVnd(),
            p.shopLimit(),
            p.monthlyOrders(),
            p.additionalMembers(),
            c.active());
    data.save(updated);
    events.append(
        "BillingPriceAvailabilityChanged",
        p.id(),
        null,
        Map.of("actorId", c.actor().userId(), "active", c.active()));
    return SubscriptionResponseMapper.price(updated);
  }
}
