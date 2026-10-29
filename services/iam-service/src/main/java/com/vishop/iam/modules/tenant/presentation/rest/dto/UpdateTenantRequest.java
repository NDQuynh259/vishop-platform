package com.vishop.iam.modules.tenant.presentation.rest.dto;

import com.vishop.iam.modules.tenant.application.api.TenantApi.Status;
import jakarta.validation.constraints.*;

public record UpdateTenantRequest(@NotBlank @Size(max = 200) String name, @NotNull Status status) {}
