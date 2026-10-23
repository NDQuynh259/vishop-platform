package com.vishop.iam.tenant.application.dto;

import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import jakarta.validation.constraints.*;

public record ChangeTenantStatusRequest(@NotNull TenantStatus status) {}
