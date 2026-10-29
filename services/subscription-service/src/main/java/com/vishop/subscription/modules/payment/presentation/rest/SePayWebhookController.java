package com.vishop.subscription.modules.payment.presentation.rest;

import com.vishop.subscription.modules.payment.application.command.ReceiveSePayWebhookCommand;
import com.vishop.subscription.modules.payment.application.port.WebhookSignaturePort;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import java.nio.*;
import java.nio.charset.*;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class SePayWebhookController {
  private final WebhookSignaturePort signature;
  private final CommandBus commands;

  public SePayWebhookController(WebhookSignaturePort signature, CommandBus commands) {
    this.signature = signature;
    this.commands = commands;
  }

  @PostMapping(
      value = ApiPaths.V1 + "/billing/webhooks/sepay",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Boolean> receive(
      @RequestHeader(name = "X-SePay-Timestamp", required = false) String timestamp,
      @RequestHeader(name = "X-SePay-Signature", required = false) String signed,
      @RequestBody byte[] raw) {
    if (raw.length > 65536) throw new BadRequestException("SePay payload exceeds 64 KiB");
    signature.verify(timestamp, signed, raw);
    try {
      String body =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(raw))
              .toString();
      commands.dispatch(new ReceiveSePayWebhookCommand(body));
    } catch (CharacterCodingException invalid) {
      throw new BadRequestException("SePay payload must be UTF-8");
    }
    return Map.of("success", true);
  }
}
