package com.vishop.iam.modules.tenant.application.mapper;

import com.vishop.iam.contracts.tenant.TenantResponseDto;
import com.vishop.iam.modules.tenant.domain.entity.Tenant;

public final class TenantResponseMapper {
  private TenantResponseMapper() {}

  public static TenantResponseDto from(Tenant tenant) {
    return new TenantResponseDto(
        tenant.id(),
        tenant.code().value(),
        tenant.name(),
        tenant.status().name(),
        tenant.createdAt());
  }
}
