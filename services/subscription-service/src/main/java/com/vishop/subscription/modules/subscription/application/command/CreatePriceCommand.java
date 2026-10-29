package com.vishop.subscription.modules.subscription.application.command;

import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.domain.valueobject.BillingCycle;
import com.vishop.subscription.shared.application.dto.AuthenticatedActor;
import io.github.coreplatform.common.domain.cqrs.Command;

public record CreatePriceCommand(
    AuthenticatedActor actor,
    String planCode,
    String name,
    BillingCycle cycle,
    long amountVnd,
    int shopLimit,
    long monthlyOrders,
    int additionalMembers)
    implements Command<PriceView> {}
