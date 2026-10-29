package com.vishop.subscription.modules.payment.domain.entity;

import java.time.Instant;

public record BankTransfer(
    long id,
    String bank,
    String accountNumber,
    String subAccount,
    String code,
    String referenceCode,
    String transferType,
    long amountVnd,
    Instant occurredAt) {}
