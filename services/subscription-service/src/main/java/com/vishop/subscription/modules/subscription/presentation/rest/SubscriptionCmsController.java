package com.vishop.subscription.modules.subscription.presentation.rest;

import com.vishop.subscription.infra.security.SubscriptionAuthorization;
import com.vishop.subscription.modules.subscription.application.command.CreatePriceCommand;
import com.vishop.subscription.modules.subscription.application.command.SetPriceAvailabilityCommand;
import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.presentation.rest.dto.CreatePriceRequest;
import com.vishop.subscription.modules.subscription.presentation.rest.dto.PriceAvailabilityRequest;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/cms/billing")
public class SubscriptionCmsController {
  private final CommandBus commands;

  public SubscriptionCmsController(CommandBus commands) {
    this.commands = commands;
  }

  @PostMapping("/prices")
  @PreAuthorize("@subscriptionAuthorization.isCms(authentication)")
  public PriceView createPrice(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @Valid @RequestBody CreatePriceRequest request) {
    return commands.dispatch(
        new CreatePriceCommand(
            SubscriptionAuthorization.actor(actor),
            request.planCode(),
            request.name(),
            request.cycle(),
            request.amountVnd(),
            request.shopLimit(),
            request.monthlyOrders(),
            request.additionalMembers()));
  }

  @PutMapping("/prices/{id}/availability")
  @PreAuthorize("@subscriptionAuthorization.isCms(authentication)")
  public PriceView availability(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @PathVariable UUID id,
      @Valid @RequestBody PriceAvailabilityRequest request) {
    return commands.dispatch(
        new SetPriceAvailabilityCommand(
            SubscriptionAuthorization.actor(actor), id, request.active()));
  }
}
