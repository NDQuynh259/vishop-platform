package com.vishop.iam.tenant.domain.ports;

import com.vishop.iam.tenant.domain.entities.Tenant;
import java.util.List;
import java.util.UUID;

public interface TenantRepositoryPort {
  Tenant tenant(UUID id);

  void lockTenant(UUID id);

  List<Tenant> tenants(int limit, int offset);

  void save(Tenant tenant);
}
