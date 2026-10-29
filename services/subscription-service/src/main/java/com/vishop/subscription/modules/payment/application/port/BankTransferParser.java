package com.vishop.subscription.modules.payment.application.port;

import com.vishop.subscription.modules.payment.domain.entity.BankTransfer;

public interface BankTransferParser {
  BankTransfer parse(String raw);

  boolean destinationMatches(BankTransfer transfer);
}
