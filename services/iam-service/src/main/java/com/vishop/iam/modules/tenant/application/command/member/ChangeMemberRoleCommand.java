package com.vishop.iam.modules.tenant.application.command.member;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeMemberRoleCommand(AuthenticatedUser actor, UUID tenant, UUID user, UUID role)
    implements Command<Void> {}
