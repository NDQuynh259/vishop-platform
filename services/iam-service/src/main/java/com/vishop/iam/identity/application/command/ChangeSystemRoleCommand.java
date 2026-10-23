package com.vishop.iam.identity.application.command;

import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record ChangeSystemRoleCommand(IamActor actor, UUID user, SystemRole role)
    implements Command<Void> {}
