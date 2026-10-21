package io.github.coreplatform.common.web.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Unified metadata for both Cursor-based and Offset-based pagination.
 *
 * <p>For Cursor pagination: only hasNextPage and nextCursor are populated.
 * For Offset pagination: page, limit, total, totalPages, and hasNextPage are populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"page", "limit", "total", "totalPages", "hasNextPage", "nextCursor"})
public record PageInfo(
        Boolean hasNextPage,
        String nextCursor,
        Integer page,
        Integer limit,
        Long total,
        Integer totalPages) {

    // #region ofCursor
    public static PageInfo ofCursor(boolean hasNextPage, String nextCursor) {
        return new PageInfo(hasNextPage, nextCursor, null, null, null, null);
    }
    // #endregion

    // #region ofOffset
    public static PageInfo ofOffset(int page, int limit, long total) {
        int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
        boolean hasNext = page < totalPages;
        return new PageInfo(hasNext, null, page, limit, total, totalPages);
    }
    // #endregion
}
