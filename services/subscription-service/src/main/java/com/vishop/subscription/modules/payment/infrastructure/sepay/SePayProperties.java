package com.vishop.subscription.modules.payment.infrastructure.sepay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vishop.subscription.sepay")
@Getter
@Setter
public class SePayProperties {
  private boolean enabled;
  private String webhookSecret = "";
  private String bank = "";
  private String accountNumber = "";
  private String accountHolder = "";
  private String qrAccountNumber = "";
  private String subAccount = "";
  private String memoPrefix = "";

  @jakarta.annotation.PostConstruct
  void validate() {
    if (enabled
        && (webhookSecret.length() < 32
            || bank.isBlank()
            || accountNumber.isBlank()
            || accountHolder.isBlank()))
      throw new IllegalStateException(
          "Configure SePay webhook secret (32+ chars), bank, account and holder before enabling payments");
  }
}
