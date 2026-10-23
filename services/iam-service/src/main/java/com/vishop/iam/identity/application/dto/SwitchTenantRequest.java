package com.vishop.iam.identity.application.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record SwitchTenantRequest(@NotNull UUID tenantId) {}
