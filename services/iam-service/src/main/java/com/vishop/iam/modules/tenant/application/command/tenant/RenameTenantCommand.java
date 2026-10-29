package com.vishop.iam.modules.tenant.application.command.tenant;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record RenameTenantCommand(AuthenticatedUser actor, UUID id, String name)
    implements Command<TenantResponseDto> {}
