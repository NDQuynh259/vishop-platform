package com.vishop.subscription.modules.payment.application.api;

import java.time.Instant;

/** Payment instructions contract; no subscription entities or persistence types are exported. */
public interface PaymentApi {
  record InstructionsRequest(
      String paymentCode, long remainingAmountVnd, boolean payable, Instant expiresAt) {}

  record Instructions(
      String qrUrl, String bank, String accountNumber, String accountHolder, String transferMemo) {}

  void requireConfigured();

  Instructions instructions(InstructionsRequest request);
}
