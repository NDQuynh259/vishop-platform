package com.vishop.iam.modules.tenant.application.command.role;

import com.vishop.iam.modules.tenant.application.dto.RoleResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Set;
import java.util.UUID;

public record UpdateRoleCommand(
    AuthenticatedUser actor, UUID tenant, UUID role, String name, Set<String> permissions)
    implements Command<RoleResponseDto> {}
