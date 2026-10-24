package com.vishop.iam.modules.tenant.application.command;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeTenantStatusCommand(IamActor actor, UUID tenant, TenantStatus status)
    implements Command<TenantResponseDto> {}
