package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.modules.access.application.dto.RoleResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Set;
import java.util.UUID;

public record CreateRoleCommand(
    AuthenticatedUser actor, UUID tenant, String name, Set<String> permissions)
    implements Command<RoleResponseDto> {}
