package com.vishop.iam.modules.tenant.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ChangeMemberRoleRequest(@NotNull UUID roleId) {}
