package io.github.coreplatform.common.web.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import org.junit.jupiter.api.Test;

class PaginationRequestTest {

  // #region paginationRequestPrioritizesCursor
  @Test
  void paginationRequestPrioritizesCursorWhenProvided() {
    // When cursor is provided, even if page is provided, cursor wins
    PaginationRequest req = new PaginationRequest("cur123", 5, 30);
    assertTrue(req.hasCursor());
    assertTrue(req.isCursor());
    assertFalse(req.isOffset());
    assertEquals("cur123", req.cursor());
    assertEquals(30, req.limit());

    CursorParams cursorParams = req.toCursorParams();
    assertEquals("cur123", cursorParams.cursor());
    assertEquals(30, cursorParams.limit());
  }

  @Test
  void paginationRequestCursorClamping() {
    PaginationRequest req = new PaginationRequest("token123", 999);
    assertEquals(PaginationRequest.MAX_LIMIT, req.limit());
    assertEquals("token123", req.cursor());
  }

  // #endregion

  // #region paginationRequestOffsetFallback
  @Test
  void paginationRequestFallsBackToOffsetWhenCursorAbsent() {
    // When cursor is absent, falls back to offset
    PaginationRequest req = new PaginationRequest(null, 3, 25);
    assertFalse(req.hasCursor());
    assertFalse(req.isCursor());
    assertTrue(req.isOffset());
    assertEquals(3, req.page());
    assertEquals(25, req.limit());

    OffsetPagination offset = req.toOffsetPagination();
    assertEquals(3, offset.page());
    assertEquals(25, offset.limit());
  }

  @Test
  void paginationRequestDefaultsWhenBothAbsent() {
    // When neither cursor nor page is provided: cursor absent -> offset defaults to page 1
    PaginationRequest req = new PaginationRequest(null, null, null);
    assertFalse(req.hasCursor());
    assertFalse(req.isCursor());
    assertTrue(req.isOffset());
    assertEquals(1, req.page());
    assertEquals(20, req.limit());

    OffsetPagination offset = req.toOffsetPagination();
    assertEquals(1, offset.page());
    assertEquals(20, offset.limit());
  }

  @Test
  void paginationRequestTreatsBlankCursorAsAbsent() {
    PaginationRequest req = new PaginationRequest("   ", 2, 10);
    assertFalse(req.hasCursor());
    assertTrue(req.isOffset());
    assertEquals(2, req.page());
    assertNull(req.cursor());
  }

  @Test
  void paginationRequestClampsNegativePageAndLimit() {
    PaginationRequest req = new PaginationRequest(null, -5, 999);
    assertEquals(1, req.page());
    assertEquals(PaginationRequest.MAX_LIMIT, req.limit());
  }
  // #endregion
}
