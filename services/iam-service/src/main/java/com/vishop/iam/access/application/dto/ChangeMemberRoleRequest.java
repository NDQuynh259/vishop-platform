package com.vishop.iam.access.application.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ChangeMemberRoleRequest(@NotNull UUID roleId) {}
