package com.vishop.iam.composition.onboarding.application.command;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;

public record CreateTenantCommand(IamActor actor, String code, String name)
    implements Command<TenantResponseDto> {}
