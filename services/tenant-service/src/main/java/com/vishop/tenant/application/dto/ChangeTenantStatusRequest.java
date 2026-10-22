package com.vishop.tenant.application.dto;

import com.vishop.tenant.domain.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload to change tenant status")
public record ChangeTenantStatusRequest(
        @Schema(description = "Target tenant lifecycle status", example = "SUSPENDED")
        @NotNull(message = "status is required")
        TenantStatus status) {
}
