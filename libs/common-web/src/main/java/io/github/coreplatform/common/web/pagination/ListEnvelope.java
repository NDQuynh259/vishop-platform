package io.github.coreplatform.common.web.pagination;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.List;

/**
 * Envelope for collections supporting both Cursor-based and Offset-based pagination.
 *
 * @param object always "list"
 * @param url request URL
 * @param data page items
 * @param pageInfo pagination metadata
 */
@JsonPropertyOrder({"object", "url", "data", "pageInfo"})
public record ListEnvelope<T>(
        String object,
        String url,
        List<T> data,
        PageInfo pageInfo) {

    // #region from
    /**
     * Converts a domain/application PagedResult into a web ListEnvelope.
     *
     * @param url request URL
     * @param pagedResult domain/application paginated result
     * @return web ListEnvelope
     */
    public static <T> ListEnvelope<T> from(String url, PagedResult<T> pagedResult) {
        if (pagedResult == null) {
            return null;
        }
        if (pagedResult.page() != null || pagedResult.total() != null) {
            int page = pagedResult.page() != null ? pagedResult.page() : 1;
            int limit = pagedResult.limit() != null ? pagedResult.limit() : pagedResult.items().size();
            long total = pagedResult.total() != null ? pagedResult.total() : 0L;
            return ofOffset(url, pagedResult.items(), page, limit, total);
        }
        return ofCursor(url, pagedResult.items(), pagedResult.hasNextPage(), pagedResult.nextCursor());
    }
    // #endregion

    // #region of
    /**
     * Default factory method for cursor pagination (backward-compatible).
     */
    public static <T> ListEnvelope<T> of(String url, List<T> data, boolean hasNextPage, String nextCursor) {
        return ofCursor(url, data, hasNextPage, nextCursor);
    }
    // #endregion

    // #region ofCursor
    public static <T> ListEnvelope<T> ofCursor(String url, List<T> data, boolean hasNextPage, String nextCursor) {
        return new ListEnvelope<>("list", url, data, PageInfo.ofCursor(hasNextPage, nextCursor));
    }
    // #endregion

    // #region ofOffset
    public static <T> ListEnvelope<T> ofOffset(String url, List<T> data, int page, int limit, long total) {
        return new ListEnvelope<>("list", url, data, PageInfo.ofOffset(page, limit, total));
    }
    // #endregion
}
