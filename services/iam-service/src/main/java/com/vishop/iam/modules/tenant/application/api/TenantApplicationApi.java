package com.vishop.iam.modules.tenant.application.api;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantCode;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TenantApplicationApi implements TenantApi {
  private final TenantRepositoryPort tenants;
  private final IamEventPublisherPort outbox;

  public TenantApplicationApi(TenantRepositoryPort tenants, IamEventPublisherPort outbox) {
    this.tenants = tenants;
    this.outbox = outbox;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public TenantResponseDto create(UUID ownerId, String code, String name) {
    Tenant tenant = Tenant.create(new TenantCode(code), name, ownerId);
    tenants.save(tenant);
    tenant.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseMapper.from(tenant);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockTenant(UUID tenantId) {
    tenants.lockTenant(tenantId);
  }

  public boolean isActive(UUID tenantId) {
    return tenants.tenant(tenantId).status() == TenantStatus.ACTIVE;
  }

  public TenantResponseDto view(UUID tenantId) {
    return TenantResponseMapper.from(tenants.tenant(tenantId));
  }
}
