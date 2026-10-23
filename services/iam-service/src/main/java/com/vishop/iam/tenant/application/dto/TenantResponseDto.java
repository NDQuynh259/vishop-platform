package com.vishop.iam.tenant.application.dto;

import com.vishop.iam.tenant.domain.entities.Tenant;
import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantResponseDto(
    UUID id, String code, String name, TenantStatus status, Instant createdAt) {
  public static TenantResponseDto from(Tenant tenant) {
    return new TenantResponseDto(
        tenant.id(), tenant.code().value(), tenant.name(), tenant.status(), tenant.createdAt());
  }
}
