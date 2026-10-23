package com.vishop.tenant.application.dto;

import com.vishop.tenant.domain.entities.Tenant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Tenant resource response representation")
public record TenantResponseDto(
        @Schema(description = "Unique tenant UUID identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID id,

        @Schema(description = "Tenant unique code/slug", example = "my-store-vn")
        String code,

        @Schema(description = "Display name of the tenant", example = "My Store Vietnam")
        String name,

        @Schema(description = "Lifecycle status of the tenant", example = "ACTIVE")
        String status,

        @Schema(description = "Timestamp when the tenant was created in UTC", example = "2026-10-22T08:00:00Z")
        Instant createdAt,

        @Schema(description = "Timestamp when the tenant was last modified in UTC", example = "2026-10-22T08:30:00Z")
        Instant updatedAt) {

    // #region from
    public static TenantResponseDto from(Tenant tenant) {
        return new TenantResponseDto(
                tenant.getId(),
                tenant.getCode().value(),
                tenant.getName(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
    // #endregion
}
