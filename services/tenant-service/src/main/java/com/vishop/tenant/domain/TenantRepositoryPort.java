package com.vishop.tenant.domain;

import io.github.coreplatform.common.domain.DomainRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository port for Tenant aggregate root.
 */
public interface TenantRepositoryPort extends DomainRepository<Tenant, UUID> {

    // #region findByCode
    Optional<Tenant> findByCode(String code);
    // #endregion

    // #region existsByCode
    boolean existsByCode(String code);
    // #endregion

    // #region findTenants
    /**
     * Cursor-based list of tenants ordered by createdAt desc, id desc.
     */
    List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit);
    // #endregion
}
