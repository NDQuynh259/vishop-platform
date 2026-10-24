package com.vishop.iam.modules.identity.presentation.rest.dto;

import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import jakarta.validation.constraints.*;

public record ChangeSystemRoleRequest(@NotNull SystemRole systemRole) {}
