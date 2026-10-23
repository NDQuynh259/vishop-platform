package com.vishop.iam.tenant.application.api;

import com.vishop.iam.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.tenant.domain.ports.TenantRepositoryPort;
import com.vishop.iam.tenant.domain.valueobjects.TenantStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TenantApi {
  private final TenantRepositoryPort tenants;

  public TenantApi(TenantRepositoryPort tenants) {
    this.tenants = tenants;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockTenant(UUID tenantId) {
    tenants.lockTenant(tenantId);
  }

  public boolean isActive(UUID tenantId) {
    return tenants.tenant(tenantId).status() == TenantStatus.ACTIVE;
  }

  public TenantResponseDto view(UUID tenantId) {
    return TenantResponseDto.from(tenants.tenant(tenantId));
  }
}
