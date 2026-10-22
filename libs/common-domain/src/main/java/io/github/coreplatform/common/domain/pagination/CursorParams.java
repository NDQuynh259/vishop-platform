package io.github.coreplatform.common.domain.pagination;

/**
 * Value object representing cursor-based pagination input parameters.
 *
 * <p>Safely normalizes:
 * <ul>
 *   <li>{@code cursor}: opaque string cursor token (optional).</li>
 *   <li>{@code limit}: maximum items per page (defaults to 20, clamped between 1 and 100).</li>
 * </ul>
 *
 * @param cursor opaque cursor token from client
 * @param limit maximum items per page
 */
public record CursorParams(String cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    // #region CursorParams
    public CursorParams {
        limit = Math.clamp(limit <= 0 ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
    }
    // #endregion

    // #region of
    public static CursorParams of(String cursor, int limit) {
        return new CursorParams(cursor, limit);
    }

    public static CursorParams ofDefault() {
        return new CursorParams(null, DEFAULT_LIMIT);
    }
    // #endregion

    // #region decodeCursor
    /**
     * Decodes the raw cursor token into a typed {@link CursorPagination} instance.
     *
     * @return decoded CursorPagination, or {@code null} if cursor token is null or blank
     */
    public CursorPagination decodeCursor() {
        return CursorPagination.decode(cursor);
    }
    // #endregion
}
