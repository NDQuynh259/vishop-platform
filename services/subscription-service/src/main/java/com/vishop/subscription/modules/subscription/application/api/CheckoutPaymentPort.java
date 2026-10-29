package com.vishop.subscription.modules.subscription.application.api;

import java.time.Instant;

/** Subscription-owned outbound port for checkout payment instructions. */
public interface CheckoutPaymentPort {
  record Request(String paymentCode, long remainingAmountVnd, boolean payable, Instant expiresAt) {}

  record Instructions(
      String qrUrl, String bank, String accountNumber, String accountHolder, String transferMemo) {}

  void requireConfigured();

  Instructions instructions(Request request);
}
