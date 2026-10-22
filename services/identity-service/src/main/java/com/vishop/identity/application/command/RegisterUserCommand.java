package com.vishop.identity.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;
import com.vishop.identity.application.dto.AuthResponseDto;
import java.util.UUID;

/**
 * Command representing user registration intention.
 */
public record RegisterUserCommand(
        String email,
        String password,
        String fullName,
        UUID tenantId,
        String role
) implements Command<AuthResponseDto> {
}
