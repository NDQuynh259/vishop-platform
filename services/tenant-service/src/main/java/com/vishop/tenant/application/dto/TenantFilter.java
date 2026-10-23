package com.vishop.tenant.application.dto;

import com.vishop.tenant.domain.valueobjects.TenantStatus;

/**
 * Filter criteria for querying tenants.
 *
 * @param search keyword matching tenant code or name (case-insensitive)
 * @param status filter by tenant status (ACTIVE, SUSPENDED, ARCHIVED)
 */
public record TenantFilter(
        String search,
        TenantStatus status) {

    // #region empty
    public static TenantFilter empty() {
        return new TenantFilter(null, null);
    }
    // #endregion
}
