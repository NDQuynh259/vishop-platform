package com.vishop.iam.tenant.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RenameTenantCommand(IamActor actor, UUID id, String name)
    implements Command<TenantResponseDto> {}
