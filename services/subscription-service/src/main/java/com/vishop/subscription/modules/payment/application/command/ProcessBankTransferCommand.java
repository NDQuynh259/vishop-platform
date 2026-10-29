package com.vishop.subscription.modules.payment.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;

public record ProcessBankTransferCommand(long inboxId) implements Command<Void> {}
