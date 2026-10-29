package com.vishop.subscription.composition.checkout;

import com.vishop.subscription.modules.payment.application.api.PaymentApi;
import com.vishop.subscription.modules.subscription.application.api.CheckoutPaymentPort;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPaymentInstructionsAdapter implements CheckoutPaymentPort {
  private final PaymentApi payments;

  public SubscriptionPaymentInstructionsAdapter(PaymentApi payments) {
    this.payments = payments;
  }

  public void requireConfigured() {
    payments.requireConfigured();
  }

  public Instructions instructions(Request request) {
    var p =
        payments.instructions(
            new PaymentApi.InstructionsRequest(
                request.paymentCode(),
                request.remainingAmountVnd(),
                request.payable(),
                request.expiresAt()));
    return new Instructions(
        p.qrUrl(), p.bank(), p.accountNumber(), p.accountHolder(), p.transferMemo());
  }
}
