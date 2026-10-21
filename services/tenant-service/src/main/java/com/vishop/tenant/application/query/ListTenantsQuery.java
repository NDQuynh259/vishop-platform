package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import com.vishop.tenant.application.dto.TenantResponseDto;

public record ListTenantsQuery(int limit, String cursor, String requestUrl)
        implements Query<ListEnvelope<TenantResponseDto>> {

    // #region ListTenantsQuery
    public ListTenantsQuery(int limit, String cursor, String requestUrl) {
        this.limit = Math.clamp(limit <= 0 ? 20 : limit, 1, 100);
        this.cursor = cursor;
        this.requestUrl = requestUrl != null ? requestUrl : "/api/v1/tenants";
    }
    // #endregion
}
