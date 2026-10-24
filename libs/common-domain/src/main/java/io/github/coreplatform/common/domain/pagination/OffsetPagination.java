package io.github.coreplatform.common.domain.pagination;

/**
 * Value object representing offset-based pagination input parameters.
 *
 * <p>Safely normalizes:
 *
 * <ul>
 *   <li>{@code page}: 1-indexed page number (defaults to 1, minimum 1).
 *   <li>{@code limit}: maximum items per page (defaults to 20, clamped between 1 and 100).
 * </ul>
 *
 * @param page 1-indexed page number
 * @param limit maximum items per page
 */
public record OffsetPagination(int page, int limit) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 100;

  // #region OffsetPagination
  public OffsetPagination {
    page = page <= 0 ? DEFAULT_PAGE : page;
    limit = Math.clamp(limit <= 0 ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
  }

  // #endregion

  // #region of
  public static OffsetPagination of(int page, int limit) {
    return new OffsetPagination(page, limit);
  }

  public static OffsetPagination ofDefault() {
    return new OffsetPagination(DEFAULT_PAGE, DEFAULT_LIMIT);
  }

  // #endregion

  // #region offset
  /** Calculates the zero-based SQL/database offset: {@code (page - 1) * limit}. */
  public long offset() {
    return (long) (page - 1) * limit;
  }

  /**
   * Calculates the zero-based page index for Spring Data {@code PageRequest.of(pageIndex, limit)}.
   */
  public int pageIndex() {
    return page - 1;
  }
  // #endregion
}
