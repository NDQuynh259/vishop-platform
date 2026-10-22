package io.github.coreplatform.common.web.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Unified metadata for both Cursor-based and Offset-based pagination.
 *
 * <p>For Cursor pagination: only hasNextPage and nextCursor are populated.
 * For Offset pagination: page, limit, total, totalPages, and hasNextPage are populated.
 */
@Schema(description = "Pagination metadata object supporting both cursor and offset models")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"page", "limit", "total", "totalPages", "hasNextPage", "nextCursor"})
public record PageInfo(
        @Schema(description = "Indicates if there is another page of results available", example = "true")
        Boolean hasNextPage,

        @Schema(description = "Opaque cursor token to fetch the next page (cursor pagination)", example = "MTcyOTU3NDAwMDAwMDpmYjM0MzE1LTQ3MTgtNGYwYy1iOTlhLWQ2YTQwZDlkNmZhZA")
        String nextCursor,

        @Schema(description = "Current page number (1-indexed, offset pagination)", example = "1")
        Integer page,

        @Schema(description = "Page size limit", example = "20")
        Integer limit,

        @Schema(description = "Total number of items matching filter (offset pagination)", example = "150")
        Long total,

        @Schema(description = "Total number of pages (offset pagination)", example = "8")
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
