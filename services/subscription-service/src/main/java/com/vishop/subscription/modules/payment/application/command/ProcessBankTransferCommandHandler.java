package com.vishop.subscription.modules.payment.application.command;

import com.vishop.subscription.modules.payment.application.api.SubscriptionPaymentsPort;
import com.vishop.subscription.modules.payment.application.port.*;
import com.vishop.subscription.shared.application.port.BusinessEventPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProcessBankTransferCommandHandler
    implements CommandHandler<ProcessBankTransferCommand, Void> {
  private final WebhookInboxPort inbox;
  private final BankTransferParser parser;
  private final SubscriptionPaymentsPort subscriptions;
  private final BusinessEventPort events;

  public ProcessBankTransferCommandHandler(
      WebhookInboxPort inbox,
      BankTransferParser parser,
      SubscriptionPaymentsPort subscriptions,
      BusinessEventPort events) {
    this.inbox = inbox;
    this.parser = parser;
    this.subscriptions = subscriptions;
    this.events = events;
  }

  @Transactional
  public Void handle(ProcessBankTransferCommand command) {
    var message = inbox.lock(command.inboxId()).orElseThrow();
    if (!"PENDING".equals(message.status())) return null;
    var transfer = parser.parse(message.rawPayload());
    if (inbox.duplicateReference(transfer)) {
      inbox.complete(transfer.id());
      return null;
    }
    var candidate =
        transfer.code() == null
            ? Optional.<SubscriptionPaymentsPort.OrderReference>empty()
            : subscriptions.findOrder(transfer.code());
    if (candidate.isEmpty()
        || !"in".equals(transfer.transferType())
        || !parser.destinationMatches(transfer)) {
      String reason =
          !"in".equals(transfer.transferType())
              ? "OUTGOING_TRANSFER"
              : !parser.destinationMatches(transfer) ? "WRONG_DESTINATION" : "UNKNOWN_PAYMENT_CODE";
      inbox.record(transfer, null, "REVIEW", reason);
      inbox.complete(transfer.id());
      events.append(
          "BillingPaymentNeedsReview",
          UuidV7.nextId(),
          null,
          Map.of("paymentId", transfer.id(), "amountVnd", transfer.amountVnd(), "reason", reason));
      return null;
    }
    var ref = candidate.get();
    var outcome =
        subscriptions.applyPayment(
            ref.id(), ref.tenantId(), transfer.amountVnd(), transfer.occurredAt());
    String state = outcome.review() ? "REVIEW" : "ALLOCATED";
    // A unique bank reference is the final race guard; any conflict rolls back subscription too.
    inbox.record(transfer, outcome.orderId(), state, outcome.reviewReason());
    inbox.complete(transfer.id());
    events.append(
        "BillingPaymentReceived",
        outcome.orderId(),
        outcome.tenantId(),
        Map.of("amountVnd", transfer.amountVnd(), "paymentId", transfer.id(), "state", state));
    return null;
  }
}
