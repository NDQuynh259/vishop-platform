package com.vishop.iam.modules.tenant.application.api;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.modules.tenant.application.mapper.TenantResponseMapper;
import com.vishop.iam.modules.tenant.application.service.TenantAuthorizationService;
import com.vishop.iam.modules.tenant.domain.entity.Tenant;
import com.vishop.iam.modules.tenant.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantCode;
import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TenantApplicationApi implements TenantApi {
  private final TenantRepositoryPort tenants;
  private final IamEventPublisherPort outbox;
  private final MembershipRepositoryPort memberships;
  private final RoleRepositoryPort roles;
  private final TenantAuthorizationService authorization;

  public TenantApplicationApi(
      TenantRepositoryPort tenants,
      IamEventPublisherPort outbox,
      MembershipRepositoryPort memberships,
      RoleRepositoryPort roles,
      TenantAuthorizationService authorization) {
    this.tenants = tenants;
    this.outbox = outbox;
    this.memberships = memberships;
    this.roles = roles;
    this.authorization = authorization;
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

  public List<TenantResponseDto> tenants(int limit, int offset) {
    return tenants.tenants(limit, offset).stream().map(TenantResponseMapper::from).toList();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TenantResponseDto rename(UUID tenantId, String name, UUID actorId) {
    Tenant tenant = tenants.tenant(tenantId);
    tenant.rename(name, actorId);
    tenants.save(tenant);
    tenant.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseMapper.from(tenant);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TenantResponseDto update(UUID tenantId, String name, Status status, UUID actorId) {
    Tenant tenant = tenants.tenant(tenantId);
    if (!tenant.name().equals(name.trim())) tenant.rename(name, actorId);
    if (!tenant.status().name().equals(status.name()))
      tenant.changeStatus(TenantStatus.valueOf(status.name()), actorId);
    tenants.save(tenant);
    tenant.pullDomainEvents().forEach(outbox::publish);
    return TenantResponseMapper.from(tenant);
  }

  public void requireAccess(AuthenticatedUser actor, UUID tenantId, String permission) {
    authorization.access(actor, tenantId, permission);
  }

  public List<UUID> activeTenantIds(UUID userId) {
    return tenants.activeTenantIds(userId);
  }

  public boolean hasActiveRole(UUID userId, UUID tenantId, UUID roleId, String roleKey) {
    return memberships.hasActiveRole(userId, tenantId, roleId, roleKey);
  }

  public Optional<Grant> activeGrant(UUID userId, UUID tenantId) {
    return memberships
        .membership(userId, tenantId)
        .filter(
            member ->
                "ACTIVE".equals(member.status())
                    && !member.requireTwoFactor()
                    && tenants.isActive(tenantId))
        .map(
            member -> {
              var role = roles.role(tenantId, member.roleId());
              String key = role.builtinKey() != null ? role.builtinKey() : "CUSTOM:" + role.id();
              return new Grant(role.id(), key, member.permissions());
            })
        .filter(
            grant -> memberships.hasActiveRole(userId, tenantId, grant.roleId(), grant.roleKey()));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void initializeOwner(UUID userId, UUID tenantId) {
    memberships.addMembership(userId, tenantId, roles.ownerRole(tenantId));
  }
}
