package com.vishop.iam.tenant.application.query;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.UUID;

public record GetTenantByIdQuery(IamActor actor, UUID id) implements Query<TenantResponseDto> {}
