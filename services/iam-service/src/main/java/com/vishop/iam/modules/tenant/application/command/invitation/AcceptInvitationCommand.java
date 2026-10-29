package com.vishop.iam.modules.tenant.application.command.invitation;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;

public record AcceptInvitationCommand(AuthenticatedUser actor, String token)
    implements Command<TenantResponseDto> {}
