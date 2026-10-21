package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.Query;
import com.vishop.tenant.application.dto.TenantResponseDto;
import java.util.UUID;

public record GetTenantByIdQuery(UUID tenantId) implements Query<TenantResponseDto> {
}
