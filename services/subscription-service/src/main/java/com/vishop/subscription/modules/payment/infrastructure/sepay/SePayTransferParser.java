package com.vishop.subscription.modules.payment.infrastructure.sepay;

import com.fasterxml.jackson.databind.*;
import com.vishop.subscription.modules.payment.application.port.BankTransferParser;
import com.vishop.subscription.modules.payment.domain.entity.BankTransfer;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class SePayTransferParser implements BankTransferParser {
  private final ObjectMapper json;
  private final SePayProperties config;

  public SePayTransferParser(ObjectMapper json, SePayProperties config) {
    this.json = json;
    this.config = config;
  }

  public BankTransfer parse(String raw) {
    try {
      JsonNode node = json.readTree(raw);
      var id = node.path("id");
      var amount = node.path("transferAmount");
      if (!id.isIntegralNumber()
          || !id.canConvertToLong()
          || id.longValue() <= 0
          || !amount.isIntegralNumber()
          || !amount.canConvertToLong()
          || amount.longValue() <= 0) throw new IllegalArgumentException();
      String bank = required(node, "gateway", 100),
          account = required(node, "accountNumber", 100),
          type = required(node, "transferType", 10);
      if (!type.equals("in") && !type.equals("out")) throw new IllegalArgumentException();
      String code = optional(node, "code", 100),
          reference = optional(node, "referenceCode", 200),
          sub = optional(node, "subAccount", 100);
      Instant occurred =
          LocalDateTime.parse(
                  required(node, "transactionDate", 30),
                  DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                      .withResolverStyle(java.time.format.ResolverStyle.STRICT))
              .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
              .toInstant();
      return new BankTransfer(
          id.longValue(), bank, account, sub, code, reference, type, amount.longValue(), occurred);
    } catch (Exception invalid) {
      throw new BadRequestException("Invalid SePay bank-transfer payload");
    }
  }

  public boolean destinationMatches(BankTransfer t) {
    return config.getBank().equalsIgnoreCase(t.bank())
        && config.getAccountNumber().equals(t.accountNumber())
        && config.getSubAccount().equals(t.subAccount() == null ? "" : t.subAccount());
  }

  private static String required(JsonNode n, String key, int max) {
    String value = optional(n, key, max);
    if (value == null || value.isBlank()) throw new IllegalArgumentException();
    return value;
  }

  private static String optional(JsonNode n, String key, int max) {
    var value = n.get(key);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.textValue().length() > max)
      throw new IllegalArgumentException();
    return value.textValue();
  }
}
