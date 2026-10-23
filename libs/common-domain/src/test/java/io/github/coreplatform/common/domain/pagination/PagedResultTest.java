package io.github.coreplatform.common.domain.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PagedResultTest {

  // #region ofCursor
  @Test
  void ofCursorCreatesCorrectResult() {
    List<String> items = List.of("A", "B");
    PagedResult<String> result = PagedResult.ofCursor(items, true, "cursor123");

    assertEquals(2, result.items().size());
    assertTrue(result.hasNextPage());
    assertEquals("cursor123", result.nextCursor());
    assertNull(result.page());
    assertNull(result.limit());
    assertNull(result.total());
    assertNull(result.totalPages());
  }

  // #endregion

  // #region ofOffset
  @Test
  void ofOffsetCreatesCorrectResult() {
    List<String> items = List.of("A", "B", "C");
    PagedResult<String> result = PagedResult.ofOffset(items, 1, 10, 25);

    assertEquals(3, result.items().size());
    assertEquals(1, result.page());
    assertEquals(10, result.limit());
    assertEquals(25L, result.total());
    assertEquals(3, result.totalPages());
    assertTrue(result.hasNextPage());
    assertNull(result.nextCursor());

    PagedResult<String> lastPage = PagedResult.ofOffset(items, 3, 10, 25);
    assertFalse(lastPage.hasNextPage());
  }
  // #endregion
}
