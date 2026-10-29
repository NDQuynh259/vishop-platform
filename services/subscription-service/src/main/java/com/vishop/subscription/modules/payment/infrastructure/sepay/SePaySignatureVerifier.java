package com.vishop.subscription.modules.payment.infrastructure.sepay;

import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SePaySignatureVerifier
    implements com.vishop.subscription.modules.payment.application.port.WebhookSignaturePort {
  private final SePayProperties config;
  private final Clock clock;

  public SePaySignatureVerifier(SePayProperties config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  public void verify(String timestamp, String signature, byte[] raw) {
    if (!config.isEnabled()) throw new UnauthorizedException("SePay webhook is disabled");
    try {
      if (timestamp == null
          || !timestamp.matches("[0-9]{10,12}")
          || signature == null
          || !signature.matches("sha256=[0-9a-fA-F]{64}")) throw new IllegalArgumentException();
      long seconds = Long.parseLong(timestamp);
      if (Math.abs(clock.instant().getEpochSecond() - seconds) > 300)
        throw new IllegalArgumentException();
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              config.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
      byte[] expected = mac.doFinal(raw);
      if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature.substring(7))))
        throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw new UnauthorizedException("Invalid SePay signature or timestamp");
    }
  }
}
