package com.vishop.subscription.modules.payment.application.port;

public interface WebhookSignaturePort {
  void verify(String timestamp, String signature, byte[] raw);
}
