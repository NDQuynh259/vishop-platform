package com.vishop.tenant.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;
import com.vishop.tenant.application.dto.TenantResponseDto;

public record CreateTenantCommand(String code, String name) implements Command<TenantResponseDto> {
}
