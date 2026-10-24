package com.vishop.iam.modules.identity.application.command;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.cqrs.Command;
import java.util.UUID;

public record SwitchTenantCommand(IamActor actor, UUID tenant) implements Command<AuthResponse> {}
