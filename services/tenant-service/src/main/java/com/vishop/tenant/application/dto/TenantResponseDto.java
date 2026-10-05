package com.vishop.tenant.application.dto;

import com.vishop.tenant.domain.Tenant;
import java.time.Instant;
import java.util.UUID;

public record TenantResponseDto(
        UUID id,
        String code,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt) {

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
}
