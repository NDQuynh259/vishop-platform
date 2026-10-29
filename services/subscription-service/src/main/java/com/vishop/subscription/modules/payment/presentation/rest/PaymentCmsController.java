package com.vishop.subscription.modules.payment.presentation.rest;

import com.vishop.subscription.infra.security.SubscriptionAuthorization;
import com.vishop.subscription.modules.payment.application.dto.*;
import com.vishop.subscription.modules.payment.application.query.*;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import io.github.coreplatform.common.web.pagination.*;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/cms/billing")
public class PaymentCmsController {
  private final QueryBus queries;

  public PaymentCmsController(QueryBus queries) {
    this.queries = queries;
  }

  @GetMapping("/payments/review")
  @PreAuthorize("@subscriptionAuthorization.isCms(authentication)")
  public ListEnvelope<PaymentReviewView> payments(
      @AuthenticationPrincipal VerifiedAccessToken actor,
      @ParameterObject PaginationRequest pagination) {
    if (pagination.hasCursor()) throw new BadRequestException("Use page/limit");
    return ListEnvelope.from(
        ApiPaths.V1 + "/cms/billing/payments/review",
        queries.dispatch(
            new ListPaymentReviewsQuery(
                SubscriptionAuthorization.actor(actor), pagination.toOffsetPagination())));
  }

  @GetMapping("/webhooks/review")
  @PreAuthorize("@subscriptionAuthorization.isCms(authentication)")
  public ListEnvelope<InboxReviewView> webhooks(
      @AuthenticationPrincipal VerifiedAccessToken actor,
      @ParameterObject PaginationRequest pagination) {
    if (pagination.hasCursor()) throw new BadRequestException("Use page/limit");
    return ListEnvelope.from(
        ApiPaths.V1 + "/cms/billing/webhooks/review",
        queries.dispatch(
            new ListWebhookReviewsQuery(
                SubscriptionAuthorization.actor(actor), pagination.toOffsetPagination())));
  }
}
