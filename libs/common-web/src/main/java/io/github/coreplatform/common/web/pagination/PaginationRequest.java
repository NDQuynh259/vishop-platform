package io.github.coreplatform.common.web.pagination;

import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.ListPagination;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Unified web request parameter object supporting both Cursor-based and Offset-based pagination.
 *
 * <p>The shared MVC argument resolver binds {@code ?cursor=...&page=...&limit=...} query parameters
 * and {@code pageMode=offset|cursor} directly into this record. Use an unannotated controller
 * argument (optionally with OpenAPI's {@code @ParameterObject}); do not use
 * {@code @ModelAttribute}, which bypasses this resolver.
 *
 * <p><strong>Resolution Rule:</strong> {@code pageMode} alone selects the mode. Cursor mode without
 * a token starts the first page. Offset mode defaults to page 1 and ignores cursor tokens.
 *
 * @param cursor opaque cursor token from client (optional)
 * @param page 1-indexed page number (optional, defaults to 1 when cursor is absent)
 * @param limit maximum items per page (defaults to 20, must be between 1 and 100)
 * @param pageMode offset or cursor; defaults to offset
 */
@Schema(description = "Unified pagination request parameters (pageMode selects cursor or offset)")
public record PaginationRequest(
    @Schema(
            description =
                "Opaque cursor token for keyset pagination. Used only when pageMode=cursor.",
            example = "MTcyOTU3NDAwMDAwMDpmYjM0MzE1LTQ3MTgtNGYwYy1iOTlhLWQ2YTQwZDlkNmZhZA")
        String cursor,
    @Min(value = 1, message = "page must be at least 1")
        @Schema(
            description = "1-indexed page number for offset pagination. Used when pageMode=offset.",
            defaultValue = "1",
            example = "1")
        Integer page,
    @Min(value = 1, message = "limit must be at least 1")
        @Max(value = MAX_LIMIT, message = "limit must not exceed 100")
        @Schema(
            description = "Maximum number of items to return (must be between 1 and 100).",
            defaultValue = "20",
            example = "20")
        Integer limit,
    @Schema(
            description =
                "Pagination mode; selects offset or cursor independently of the cursor token.",
            allowableValues = {"offset", "cursor"},
            defaultValue = "offset",
            example = "offset")
        PageMode pageMode) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_LIMIT = 20;
  public static final int MAX_LIMIT = 100;

  // #region PaginationRequest
  public PaginationRequest {
    limit = limit == null ? DEFAULT_LIMIT : limit;
    pageMode = pageMode == null ? PageMode.OFFSET : pageMode;
    if (cursor != null && cursor.isBlank()) {
      cursor = null;
    }
    if (pageMode == PageMode.OFFSET) {
      page = page == null ? Integer.valueOf(DEFAULT_PAGE) : page;
    }
  }

  public PaginationRequest(String cursor, Integer page, Integer limit) {
    this(cursor, page, limit, null);
  }

  public PaginationRequest(String cursor, Integer limit) {
    this(cursor, null, limit, PageMode.CURSOR);
  }

  public PaginationRequest(Integer page, Integer limit) {
    this(null, page, limit);
  }

  public PaginationRequest(Integer limit) {
    this(null, null, limit);
  }

  // #endregion

  // #region resolution
  /** Checks if cursor token is provided (non-null and non-blank). */
  public boolean hasCursor() {
    return cursor != null && !cursor.isBlank();
  }

  /** Selects pagination using pageMode only. */
  public boolean isCursor() {
    return pageMode == PageMode.CURSOR;
  }

  /** Selects offset pagination using pageMode only. */
  public boolean isOffset() {
    return !isCursor();
  }

  // #endregion

  // #region conversion
  public ListPagination toPagination() {
    return isCursor()
        ? ListPagination.of(toCursorParams())
        : ListPagination.of(toOffsetPagination());
  }

  /** Converts to domain {@link CursorParams}. */
  public CursorParams toCursorParams() {
    return CursorParams.of(cursor, limit);
  }

  /** Converts to domain {@link OffsetPagination}. */
  public OffsetPagination toOffsetPagination() {
    return OffsetPagination.of(page != null ? page : DEFAULT_PAGE, limit);
  }
  // #endregion
}
