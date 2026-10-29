package com.vishop.subscription.modules.subscription.presentation.rest;

import com.vishop.subscription.infra.security.SubscriptionAuthorization;
import com.vishop.subscription.modules.subscription.application.command.CancelOrderCommand;
import com.vishop.subscription.modules.subscription.application.command.CreateCheckoutCommand;
import com.vishop.subscription.modules.subscription.application.dto.CheckoutView;
import com.vishop.subscription.modules.subscription.application.dto.OrderView;
import com.vishop.subscription.modules.subscription.application.dto.PriceView;
import com.vishop.subscription.modules.subscription.application.dto.SubscriptionView;
import com.vishop.subscription.modules.subscription.application.query.GetOrderQuery;
import com.vishop.subscription.modules.subscription.application.query.GetSubscriptionQuery;
import com.vishop.subscription.modules.subscription.application.query.ListOrdersQuery;
import com.vishop.subscription.modules.subscription.application.query.ListPricesQuery;
import com.vishop.subscription.modules.subscription.presentation.rest.dto.CreateCheckoutRequest;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.*;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import io.github.coreplatform.common.web.pagination.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/billing")
public class SubscriptionController {
  private final CommandBus commands;
  private final QueryBus queries;

  public SubscriptionController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping("/plans")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).VIEW)")
  public List<PriceView> plans(@AuthenticationPrincipal VerifiedAccessToken actor) {
    return queries.dispatch(new ListPricesQuery(SubscriptionAuthorization.actor(actor)));
  }

  @GetMapping("/subscription")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).VIEW)")
  public SubscriptionView subscription(@AuthenticationPrincipal VerifiedAccessToken actor) {
    return queries.dispatch(new GetSubscriptionQuery(SubscriptionAuthorization.actor(actor)));
  }

  @PostMapping("/orders")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).MANAGE)")
  public ResponseEntity<CheckoutView> checkout(
      @AuthenticationPrincipal VerifiedAccessToken actor,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateCheckoutRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            commands.dispatch(
                new CreateCheckoutCommand(
                    SubscriptionAuthorization.actor(actor), request.priceId(), key)));
  }

  @GetMapping("/orders/{id}")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).VIEW)")
  public ResponseEntity<CheckoutView> order(
      @AuthenticationPrincipal VerifiedAccessToken actor, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.dispatch(new GetOrderQuery(SubscriptionAuthorization.actor(actor), id)));
  }

  @GetMapping("/orders")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).VIEW)")
  public ListEnvelope<OrderView> orders(
      @AuthenticationPrincipal VerifiedAccessToken actor,
      @ParameterObject PaginationRequest pagination) {
    if (pagination.hasCursor())
      throw new BadRequestException(
          "Billing order history currently supports page/limit pagination");
    return ListEnvelope.from(
        ApiPaths.V1 + "/billing/orders",
        queries.dispatch(
            new ListOrdersQuery(
                SubscriptionAuthorization.actor(actor), pagination.toOffsetPagination())));
  }

  @PostMapping("/orders/{id}/cancel")
  @PreAuthorize(
      "@subscriptionAuthorization.hasTenant(authentication) and hasAuthority(T(io.github.coreplatform.common.security.constants.CommonPermissions.Billing).MANAGE)")
  public OrderView cancel(
      @AuthenticationPrincipal VerifiedAccessToken actor, @PathVariable UUID id) {
    return commands.dispatch(new CancelOrderCommand(SubscriptionAuthorization.actor(actor), id));
  }
}
