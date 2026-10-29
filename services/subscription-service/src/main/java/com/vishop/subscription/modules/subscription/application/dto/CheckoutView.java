package com.vishop.subscription.modules.subscription.application.dto;

public record CheckoutView(
    OrderView order,
    String qrUrl,
    String bank,
    String accountNumber,
    String accountHolder,
    String transferMemo) {}
