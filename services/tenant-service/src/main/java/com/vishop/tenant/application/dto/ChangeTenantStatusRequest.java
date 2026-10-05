package com.vishop.tenant.application.dto;

import com.vishop.tenant.domain.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTenantStatusRequest(
        @NotNull(message = "status is required")
        TenantStatus status) {
}
