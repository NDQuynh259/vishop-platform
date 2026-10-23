package io.github.coreplatform.common.web.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListEnvelopeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // #region cursorPaginationEnvelope
  @Test
  void cursorPaginationEnvelope() throws Exception {
    List<String> items = List.of("item1", "item2");
    ListEnvelope<String> envelope =
        ListEnvelope.of("/api/v1/items", items, true, "next-cursor-123");

    assertEquals("list", envelope.object());
    assertEquals("/api/v1/items", envelope.url());
    assertEquals(2, envelope.data().size());
    assertTrue(envelope.pageInfo().hasNextPage());
    assertEquals("next-cursor-123", envelope.pageInfo().nextCursor());
    assertNull(envelope.pageInfo().page());
    assertNull(envelope.pageInfo().total());

    // Verify JSON serialization omits null offset fields
    String json = objectMapper.writeValueAsString(envelope);
    assertTrue(json.contains("\"nextCursor\":\"next-cursor-123\""));
    assertTrue(json.contains("\"hasNextPage\":true"));
    assertFalse(json.contains("\"page\":"));
    assertFalse(json.contains("\"total\":"));
    assertFalse(json.contains("\"totalPages\":"));
  }

  // #endregion

  // #region offsetPaginationEnvelope
  @Test
  void offsetPaginationEnvelope() throws Exception {
    List<String> items = List.of("item1", "item2", "item3");
    ListEnvelope<String> envelope = ListEnvelope.ofOffset("/api/v1/items", items, 1, 10, 25);

    assertEquals("list", envelope.object());
    assertEquals("/api/v1/items", envelope.url());
    assertEquals(3, envelope.data().size());
    assertEquals(1, envelope.pageInfo().page());
    assertEquals(10, envelope.pageInfo().limit());
    assertEquals(25L, envelope.pageInfo().total());
    assertEquals(3, envelope.pageInfo().totalPages());
    assertTrue(envelope.pageInfo().hasNextPage());
    assertNull(envelope.pageInfo().nextCursor());

    // Last page check
    ListEnvelope<String> lastPageEnvelope =
        ListEnvelope.ofOffset("/api/v1/items", items, 3, 10, 25);
    assertFalse(lastPageEnvelope.pageInfo().hasNextPage());

    // Verify JSON serialization omits null cursor field
    String json = objectMapper.writeValueAsString(envelope);
    assertTrue(json.contains("\"page\":1"));
    assertTrue(json.contains("\"limit\":10"));
    assertTrue(json.contains("\"total\":25"));
    assertTrue(json.contains("\"totalPages\":3"));
    assertTrue(json.contains("\"hasNextPage\":true"));
    assertFalse(json.contains("\"nextCursor\":"));
  }

  // #endregion

  // #region fromPagedResult
  @Test
  void fromPagedResultCursor() {
    List<String> items = List.of("item1", "item2");
    io.github.coreplatform.common.domain.pagination.PagedResult<String> paged =
        io.github.coreplatform.common.domain.pagination.PagedResult.ofCursor(items, true, "cur123");

    ListEnvelope<String> envelope = ListEnvelope.from("/api/v1/items", paged);
    assertEquals("list", envelope.object());
    assertEquals("/api/v1/items", envelope.url());
    assertEquals(2, envelope.data().size());
    assertTrue(envelope.pageInfo().hasNextPage());
    assertEquals("cur123", envelope.pageInfo().nextCursor());
  }

  @Test
  void fromPagedResultOffset() {
    List<String> items = List.of("item1", "item2");
    io.github.coreplatform.common.domain.pagination.PagedResult<String> paged =
        io.github.coreplatform.common.domain.pagination.PagedResult.ofOffset(items, 2, 10, 25);

    ListEnvelope<String> envelope = ListEnvelope.from("/api/v1/items", paged);
    assertEquals("list", envelope.object());
    assertEquals(2, envelope.pageInfo().page());
    assertEquals(10, envelope.pageInfo().limit());
    assertEquals(25L, envelope.pageInfo().total());
    assertEquals(3, envelope.pageInfo().totalPages());
    assertTrue(envelope.pageInfo().hasNextPage());
  }
  // #endregion
}
