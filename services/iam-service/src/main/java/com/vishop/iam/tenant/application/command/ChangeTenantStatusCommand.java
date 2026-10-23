package com.vishop.iam.tenant.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeTenantStatusCommand(IamActor actor, UUID tenant, TenantStatus status)
    implements Command<TenantResponseDto> {}
