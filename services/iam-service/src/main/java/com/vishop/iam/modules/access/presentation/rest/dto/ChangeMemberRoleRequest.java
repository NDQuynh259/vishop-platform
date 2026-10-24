package com.vishop.iam.modules.access.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ChangeMemberRoleRequest(@NotNull UUID roleId) {}
