package com.vishop.iam.modules.tenant.domain.port;

import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import java.util.List;
import java.util.UUID;

public interface TenantRepositoryPort {
  Tenant tenant(UUID id);

  void lockTenant(UUID id);

  List<Tenant> tenants(int limit, int offset);

  void save(Tenant tenant);
}
