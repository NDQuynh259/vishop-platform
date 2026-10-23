package com.vishop.iam.identity.application.dto;

import com.vishop.iam.identity.domain.valueobjects.SystemRole;
import jakarta.validation.constraints.*;

public record ChangeSystemRoleRequest(@NotNull SystemRole systemRole) {}
