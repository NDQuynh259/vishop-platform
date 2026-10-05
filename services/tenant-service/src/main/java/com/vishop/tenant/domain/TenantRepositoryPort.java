package com.vishop.tenant.domain;

import com.vishop.common.domain.DomainRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository port for Tenant aggregate root.
 */
public interface TenantRepositoryPort extends DomainRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Cursor-based list of tenants ordered by createdAt desc, id desc.
     */
    List<Tenant> findTenants(Instant cursorCreatedAt, UUID cursorId, int limit);
}
