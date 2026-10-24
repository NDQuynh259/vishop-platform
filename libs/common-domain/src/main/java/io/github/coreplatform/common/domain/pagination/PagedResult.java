package io.github.coreplatform.common.domain.pagination;

import java.util.Collections;
import java.util.List;

/**
 * Domain & Application level container for paginated query results. Completely free of web/HTTP
 * concepts (e.g. request URLs, HTTP envelopes).
 *
 * @param items page items
 * @param hasNextPage whether more items exist after this page
 * @param nextCursor opaque cursor for next page (cursor pagination)
 * @param page 1-indexed current page (offset pagination)
 * @param limit page size limit
 * @param total total number of items across all pages (offset pagination)
 * @param totalPages total number of pages (offset pagination)
 */
public record PagedResult<T>(
    List<T> items,
    boolean hasNextPage,
    String nextCursor,
    Integer page,
    Integer limit,
    Long total,
    Integer totalPages) {

  // #region PagedResult
  public PagedResult {
    items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
  }

  // #endregion

  // #region ofCursor
  public static <T> PagedResult<T> ofCursor(List<T> items, boolean hasNextPage, String nextCursor) {
    return new PagedResult<>(items, hasNextPage, nextCursor, null, null, null, null);
  }

  // #endregion

  // #region ofOffset
  public static <T> PagedResult<T> ofOffset(List<T> items, int page, int limit, long total) {
    int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
    boolean hasNext = page < totalPages;
    return new PagedResult<>(items, hasNext, null, page, limit, total, totalPages);
  }
  // #endregion
}
