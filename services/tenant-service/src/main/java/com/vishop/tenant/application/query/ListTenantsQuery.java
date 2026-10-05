package com.vishop.tenant.application.query;

import com.vishop.common.domain.cqrs.Query;
import com.vishop.common.web.ListEnvelope;
import com.vishop.tenant.application.dto.TenantResponseDto;

public record ListTenantsQuery(int limit, String cursor, String requestUrl)
        implements Query<ListEnvelope<TenantResponseDto>> {

    public ListTenantsQuery(int limit, String cursor, String requestUrl) {
        this.limit = Math.clamp(limit <= 0 ? 20 : limit, 1, 100);
        this.cursor = cursor;
        this.requestUrl = requestUrl != null ? requestUrl : "/api/v1/tenants";
    }
}
