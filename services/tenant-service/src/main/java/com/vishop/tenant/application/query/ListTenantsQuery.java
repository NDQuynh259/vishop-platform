package com.vishop.tenant.application.query;

import io.github.coreplatform.common.domain.cqrs.PaginatedQuery;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import com.vishop.tenant.application.dto.TenantFilter;
import com.vishop.tenant.application.dto.TenantResponseDto;

public record ListTenantsQuery(
        TenantFilter filter,
        CursorParams cursorParams,
        OffsetPagination offsetParams
) implements PaginatedQuery<TenantFilter, TenantResponseDto> {

    // #region ListTenantsQuery
    public ListTenantsQuery {
        filter = filter != null ? filter : TenantFilter.empty();
    }

    public static ListTenantsQuery ofCursor(TenantFilter filter, CursorParams cursorParams) {
        return new ListTenantsQuery(filter, cursorParams, null);
    }

    public static ListTenantsQuery ofOffset(TenantFilter filter, OffsetPagination offsetParams) {
        return new ListTenantsQuery(filter, null, offsetParams);
    }

    public ListTenantsQuery(TenantFilter filter, CursorParams cursorParams) {
        this(filter, cursorParams, null);
    }

    public ListTenantsQuery(TenantFilter filter, int limit, String cursor) {
        this(filter, CursorParams.of(cursor, limit), null);
    }

    public ListTenantsQuery(int limit, String cursor) {
        this(TenantFilter.empty(), CursorParams.of(cursor, limit), null);
    }
    // #endregion

    // #region inspection
    /**
     * True if query has explicit cursor params.
     */
    public boolean isCursor() {
        return cursorParams != null && cursorParams.cursor() != null && !cursorParams.cursor().isBlank();
    }
    // #endregion

    // #region backward-compatible accessors
    public CursorParams pagination() {
        return cursorParams != null ? cursorParams : CursorParams.ofDefault();
    }

    public int limit() {
        if (cursorParams != null) {
            return cursorParams.limit();
        }
        if (offsetParams != null) {
            return offsetParams.limit();
        }
        return 20;
    }

    public String cursor() {
        return cursorParams != null ? cursorParams.cursor() : null;
    }
    // #endregion
}
