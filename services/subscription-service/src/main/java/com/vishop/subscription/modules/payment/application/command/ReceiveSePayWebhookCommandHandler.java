package com.vishop.subscription.modules.payment.application.command;

import com.vishop.subscription.modules.payment.application.port.BankTransferParser;
import com.vishop.subscription.modules.payment.application.port.WebhookInboxPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReceiveSePayWebhookCommandHandler
    implements CommandHandler<ReceiveSePayWebhookCommand, Void> {
  private final BankTransferParser parser;
  private final WebhookInboxPort inbox;
  private final Clock clock;

  public ReceiveSePayWebhookCommandHandler(
      BankTransferParser parser, WebhookInboxPort inbox, Clock clock) {
    this.parser = parser;
    this.inbox = inbox;
    this.clock = clock;
  }

  @Transactional
  public Void handle(ReceiveSePayWebhookCommand command) {
    var transfer = parser.parse(command.rawPayload());
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      // Delivery formatting and balance/description fields may change on a replay.
      // Deduplicate by the immutable transaction facts, while preserving the original raw body.
      for (String value :
          new String[] {
            Long.toString(transfer.id()),
            transfer.bank(),
            transfer.accountNumber(),
            transfer.subAccount(),
            transfer.code(),
            transfer.referenceCode(),
            transfer.transferType(),
            Long.toString(transfer.amountVnd()),
            transfer.occurredAt().toString()
          }) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      String hash = HexFormat.of().formatHex(digest.digest());
      inbox.receive(transfer.id(), command.rawPayload(), hash, clock.instant());
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    return null;
  }
}
