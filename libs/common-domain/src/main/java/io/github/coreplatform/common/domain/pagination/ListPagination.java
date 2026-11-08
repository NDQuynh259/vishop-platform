package io.github.coreplatform.common.domain.pagination;

/** Explicit choice of pagination mode, without HTTP dependencies. */
public record ListPagination(OffsetPagination offset, CursorParams cursor) {
  public ListPagination {
    if ((offset == null) == (cursor == null))
      throw new IllegalArgumentException("Choose exactly one pagination mode");
  }

  public static ListPagination of(OffsetPagination offset) {
    return new ListPagination(offset, null);
  }

  public static ListPagination of(CursorParams cursor) {
    return new ListPagination(null, cursor);
  }

  public boolean isCursor() {
    return cursor != null;
  }

  public int limit() {
    return isCursor() ? cursor.limit() : offset.limit();
  }
}
