package com.vishop.subscription.modules.payment.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;

/** Signature verification is performed on raw bytes by the HTTP adapter before dispatch. */
public record ReceiveSePayWebhookCommand(String rawPayload) implements Command<Void> {}
