package com.vishop.iam.modules.identity.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record SwitchTenantRequest(@NotNull UUID tenantId) {}
