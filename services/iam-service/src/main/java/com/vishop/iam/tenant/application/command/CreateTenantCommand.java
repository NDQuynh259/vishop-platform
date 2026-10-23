package com.vishop.iam.tenant.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.Command;

public record CreateTenantCommand(IamActor actor, String code, String name)
    implements Command<TenantResponseDto> {}
