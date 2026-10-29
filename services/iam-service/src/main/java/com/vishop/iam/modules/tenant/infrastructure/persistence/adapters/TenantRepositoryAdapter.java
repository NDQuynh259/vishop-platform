package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataTenantRepository;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepositoryAdapter implements TenantRepositoryPort {
  private final SpringDataTenantRepository tenants;

  public TenantRepositoryAdapter(SpringDataTenantRepository tenants) {
    this.tenants = tenants;
  }

  public Tenant tenant(UUID id) {
    return tenants
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Tenant", id))
        .toDomain();
  }

  public void lockTenant(UUID id) {
    tenants.findLockedById(id).orElseThrow(() -> new EntityNotFoundException("Tenant", id));
  }

  public List<Tenant> tenants(int limit, int offset) {
    return tenants.findWindow(limit, offset).stream().map(TenantJpaEntity::toDomain).toList();
  }

  public void save(Tenant tenant) {
    tenants.saveAndFlush(TenantJpaEntity.fromDomain(tenant));
  }

  @Override
  public List<UUID> activeTenantIds(UUID userId) {
    return tenants.findActiveTenantIds(userId, TenantStatus.ACTIVE);
  }
}
