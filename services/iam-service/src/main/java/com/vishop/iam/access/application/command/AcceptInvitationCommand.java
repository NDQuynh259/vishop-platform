package com.vishop.iam.access.application.command;

import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import io.github.coreplatform.common.domain.cqrs.Command;

public record AcceptInvitationCommand(IamActor actor, String token)
    implements Command<TenantResponseDto> {}
