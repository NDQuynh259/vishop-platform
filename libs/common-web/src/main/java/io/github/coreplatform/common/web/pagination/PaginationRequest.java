package io.github.coreplatform.common.web.pagination;

import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;

/**
 * Unified web request parameter object supporting both Cursor-based and Offset-based pagination.
 *
 * <p>Spring MVC binds {@code ?cursor=...&page=...&limit=...} query parameters directly into this record.
 *
 * <p><strong>Resolution Rule:</strong>
 * Prioritizes Cursor pagination if {@code cursor} is provided (non-null and non-blank).
 * If {@code cursor} is absent, falls back to Offset pagination (defaulting {@code page} to 1).
 *
 * @param cursor opaque cursor token from client (optional)
 * @param page 1-indexed page number (optional, defaults to 1 when cursor is absent)
 * @param limit maximum items per page (defaults to 20, clamped between 1 and 100)
 */
public record PaginationRequest(
        String cursor,
        Integer page,
        Integer limit
) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    // #region PaginationRequest
    public PaginationRequest {
        limit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (cursor != null && cursor.isBlank()) {
            cursor = null;
        }
        if (cursor == null) {
            page = (page == null || page <= 0) ? Integer.valueOf(DEFAULT_PAGE) : page;
        } else if (page != null && page <= 0) {
            page = Integer.valueOf(DEFAULT_PAGE);
        }
    }

    public PaginationRequest(String cursor, Integer limit) {
        this(cursor, null, limit);
    }

    public PaginationRequest(Integer page, Integer limit) {
        this(null, page, limit);
    }

    public PaginationRequest(Integer limit) {
        this(null, null, limit);
    }
    // #endregion

    // #region resolution
    /**
     * Checks if cursor token is provided (non-null and non-blank).
     */
    public boolean hasCursor() {
        return cursor != null && !cursor.isBlank();
    }

    /**
     * Prioritizes cursor: returns true if cursor token is present.
     */
    public boolean isCursor() {
        return hasCursor();
    }

    /**
     * Falls back to offset: returns true if cursor is absent.
     */
    public boolean isOffset() {
        return !isCursor();
    }
    // #endregion

    // #region conversion
    /**
     * Converts to domain {@link CursorParams}.
     */
    public CursorParams toCursorParams() {
        return CursorParams.of(cursor, limit);
    }

    /**
     * Converts to domain {@link OffsetPagination}.
     */
    public OffsetPagination toOffsetPagination() {
        return OffsetPagination.of(page != null ? page : DEFAULT_PAGE, limit);
    }
    // #endregion
}
