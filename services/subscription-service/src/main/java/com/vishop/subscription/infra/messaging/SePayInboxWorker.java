package com.vishop.subscription.infra.messaging;

import com.vishop.subscription.modules.payment.application.command.ProcessBankTransferCommand;
import com.vishop.subscription.modules.payment.application.port.WebhookInboxPort;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "vishop.subscription.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SePayInboxWorker {
  private static final Logger log = LoggerFactory.getLogger(SePayInboxWorker.class);
  private final WebhookInboxPort inbox;
  private final CommandBus commands;

  public SePayInboxWorker(WebhookInboxPort inbox, CommandBus commands) {
    this.inbox = inbox;
    this.commands = commands;
  }

  @Scheduled(fixedDelayString = "${vishop.subscription.worker.delay-ms:1000}")
  public void process() {
    for (long id : inbox.pending(50))
      try {
        commands.dispatch(new ProcessBankTransferCommand(id));
      } catch (RuntimeException failure) {
        log.warn(
            "SePay inbox {} processing failed; durable record remains pending ({})",
            id,
            failure.getClass().getSimpleName());
      }
  }
}
