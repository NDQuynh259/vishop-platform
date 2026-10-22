package com.vishop.tenant.domain;

import io.github.coreplatform.common.domain.DomainRepository;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
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
     * Cursor-based list of tenants with optional status and search filters,
     * ordered by createdAt desc, id desc.
     */
    List<Tenant> findTenants(TenantStatus status, String search, Instant cursorCreatedAt, UUID cursorId, int limit);

    /**
     * Cursor-based list of tenants ordered by createdAt desc, id desc.
     */
    default List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit) {
        return findTenants(null, null, cursorCreatedAt, cursorId, limit);
    }
    // #endregion

    // #region findTenantsOffset
    /**
     * Offset-based list of tenants with optional status and search filters,
     * ordered by createdAt desc, id desc.
     */
    PagedResult<Tenant> findTenantsOffset(TenantStatus status, String search, OffsetPagination pagination);
    // #endregion
}
