package com.vishop.iam.modules.tenant.application.query;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetTenantByIdQuery(IamActor actor, UUID id) implements Query<TenantResponseDto> {}
