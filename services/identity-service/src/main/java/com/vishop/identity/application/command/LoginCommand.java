package com.vishop.identity.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;
import com.vishop.identity.application.dto.AuthResponseDto;

/**
 * Command representing user login / authentication intention.
 */
public record LoginCommand(
        String email,
        String password
) implements Command<AuthResponseDto> {
}
