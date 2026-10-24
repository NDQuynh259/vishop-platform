package com.vishop.iam.modules.tenant.presentation.rest.dto;

import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import jakarta.validation.constraints.*;

public record ChangeTenantStatusRequest(@NotNull TenantStatus status) {}
