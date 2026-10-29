package com.vishop.subscription.modules.payment.infrastructure.sepay;

import com.vishop.subscription.modules.payment.application.api.PaymentApi;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class SePayQrAdapter implements PaymentApi {
  private final SePayProperties config;
  private final java.time.Clock clock;

  public SePayQrAdapter(SePayProperties config, java.time.Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  public void requireConfigured() {
    if (!config.isEnabled()) throw new BadRequestException("QR payments are not enabled");
  }

  public Instructions instructions(InstructionsRequest request) {
    String memo =
        (config.getMemoPrefix().isBlank() ? "" : config.getMemoPrefix().trim() + " ")
            + request.paymentCode();
    String account =
        config.getQrAccountNumber().isBlank()
            ? config.getAccountNumber()
            : config.getQrAccountNumber();
    String url = null;
    if (config.isEnabled() && request.payable() && clock.instant().isBefore(request.expiresAt()))
      url =
          "https://vietqr.app/img?acc="
              + encode(account)
              + "&bank="
              + encode(config.getBank())
              + "&amount="
              + request.remainingAmountVnd()
              + "&des="
              + encode(memo);
    return new Instructions(url, config.getBank(), account, config.getAccountHolder(), memo);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
