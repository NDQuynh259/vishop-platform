package com.vishop.iam.modules.tenant.application.command;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RenameTenantCommand(IamActor actor, UUID id, String name)
    implements Command<TenantResponseDto> {}
