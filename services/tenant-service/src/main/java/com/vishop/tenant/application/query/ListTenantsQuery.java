package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import com.vishop.tenant.application.dto.TenantResponseDto;

public record ListTenantsQuery(int limit, String cursor)
        implements Query<PagedResult<TenantResponseDto>> {

    // #region ListTenantsQuery
    public ListTenantsQuery(int limit, String cursor) {
        this.limit = Math.clamp(limit <= 0 ? 20 : limit, 1, 100);
        this.cursor = cursor;
    }
    // #endregion
}
