package io.github.coreplatform.common.web.pagination;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Envelope for collections supporting both Cursor-based and Offset-based pagination.
 *
 * @param object always "list"
 * @param url request URL
 * @param data page items
 * @param pageInfo pagination metadata
 */
@Schema(description = "Standard collection envelope containing paginated items and metadata")
@JsonPropertyOrder({"object", "url", "data", "pageInfo"})
public record ListEnvelope<T>(
    @Schema(description = "Object kind descriptor, always 'list'", example = "list") String object,
    @Schema(description = "Canonical request URL", example = ApiPaths.V1 + "/tenants") String url,
    @Schema(description = "List of domain items returned for this page") List<T> data,
    @Schema(description = "Pagination metadata for cursor or offset pagination")
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

  /** Pages a bounded catalog. Database lists paginate in their read adapter. */
  public static <T> ListEnvelope<T> from(String url, List<T> catalog, PaginationRequest request) {
    return from(url, catalog, request, Object::toString);
  }

  public static <T> ListEnvelope<T> from(
      String url,
      List<T> catalog,
      PaginationRequest request,
      java.util.function.Function<T, String> key) {
    if (request.isOffset()) {
      var pagination = request.toOffsetPagination();
      int from = (int) Math.min(pagination.offset(), catalog.size());
      int to = Math.min(from + pagination.limit(), catalog.size());
      return ofOffset(
          url, catalog.subList(from, to), pagination.page(), pagination.limit(), catalog.size());
    }
    var keys =
        io.github.coreplatform.common.domain.pagination.ListCursor.decode(request.cursor(), url, 1);
    int from = 0;
    if (!keys.isEmpty()) {
      while (from < catalog.size() && !keys.getFirst().equals(key.apply(catalog.get(from)))) from++;
      if (from == catalog.size())
        throw new io.github.coreplatform.common.domain.exception.BadRequestException(
            "Invalid catalog cursor");
      from++;
    }
    int to = Math.min(from + request.limit(), catalog.size());
    boolean hasNext = to < catalog.size();
    String next =
        hasNext
            ? io.github.coreplatform.common.domain.pagination.ListCursor.encode(
                url, List.of(key.apply(catalog.get(to - 1))))
            : null;
    return ofCursor(url, catalog.subList(from, to), hasNext, next);
  }

  // #region of
  /** Default factory method for cursor pagination (backward-compatible). */
  public static <T> ListEnvelope<T> of(
      String url, List<T> data, boolean hasNextPage, String nextCursor) {
    return ofCursor(url, data, hasNextPage, nextCursor);
  }

  // #endregion

  // #region ofCursor
  public static <T> ListEnvelope<T> ofCursor(
      String url, List<T> data, boolean hasNextPage, String nextCursor) {
    return new ListEnvelope<>("list", url, data, PageInfo.ofCursor(hasNextPage, nextCursor));
  }

  // #endregion

  // #region ofOffset
  public static <T> ListEnvelope<T> ofOffset(
      String url, List<T> data, int page, int limit, long total) {
    return new ListEnvelope<>("list", url, data, PageInfo.ofOffset(page, limit, total));
  }
  // #endregion
}
