package com.vishop.subscription.modules.payment.application.port;

import com.vishop.subscription.modules.payment.domain.entity.BankTransfer;
import java.time.Instant;
import java.util.*;

public interface WebhookInboxPort {
  record Message(long id, String rawPayload, String status, Instant receivedAt) {}

  void receive(long id, String raw, String hash, Instant now);

  Optional<Message> lock(long id);

  void complete(long id);

  void conflict(long id, String reason);

  boolean duplicateReference(BankTransfer transfer);

  void record(BankTransfer transfer, UUID orderId, UUID tenantId, String status, String reason);

  List<Long> pending(int limit);
}
