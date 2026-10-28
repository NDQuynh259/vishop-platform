package com.vishop.iam.modules.identity.presentation.rest.dto;

import io.github.coreplatform.common.domain.constants.SystemRole;
import jakarta.validation.constraints.*;

public record ChangeSystemRoleRequest(@NotNull SystemRole systemRole) {}
