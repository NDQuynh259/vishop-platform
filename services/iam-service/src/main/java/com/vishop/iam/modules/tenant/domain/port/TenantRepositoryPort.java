package com.vishop.iam.modules.tenant.domain.port;

import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import io.github.coreplatform.common.domain.constants.TenantStatus;
import java.util.List;
import java.util.UUID;

public interface TenantRepositoryPort {
  Tenant tenant(UUID id);

  void lockTenant(UUID id);

  List<Tenant> tenants(int limit, int offset);

  default boolean isActive(UUID tenantId) {
    return tenant(tenantId).status() == TenantStatus.ACTIVE;
  }

  void save(Tenant tenant);
}
