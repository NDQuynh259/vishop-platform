package com.vishop.iam.modules.identity.application.command.switchtenant;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record SwitchTenantCommand(AuthenticatedUser actor, UUID tenant, String refreshToken)
    implements Command<AuthResponse> {}
