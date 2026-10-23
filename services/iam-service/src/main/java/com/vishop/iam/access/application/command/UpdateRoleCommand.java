package com.vishop.iam.access.application.command;

import com.vishop.iam.access.application.dto.RoleResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.Set;
import java.util.UUID;

public record UpdateRoleCommand(
    IamActor actor, UUID tenant, UUID role, String name, Set<String> permissions)
    implements Command<RoleResponseDto> {}
