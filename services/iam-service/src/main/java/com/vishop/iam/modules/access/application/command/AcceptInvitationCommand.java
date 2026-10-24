package com.vishop.iam.modules.access.application.command;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;

public record AcceptInvitationCommand(IamActor actor, String token)
    implements Command<TenantResponseDto> {}
