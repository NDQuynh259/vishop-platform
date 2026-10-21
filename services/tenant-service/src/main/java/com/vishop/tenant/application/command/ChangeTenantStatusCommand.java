package com.vishop.tenant.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;
import com.vishop.tenant.application.dto.TenantResponseDto;
import com.vishop.tenant.domain.TenantStatus;
import java.util.UUID;

public record ChangeTenantStatusCommand(UUID tenantId, TenantStatus newStatus) implements Command<TenantResponseDto> {
}
