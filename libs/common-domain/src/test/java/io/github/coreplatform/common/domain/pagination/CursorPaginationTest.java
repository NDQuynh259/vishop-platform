package io.github.coreplatform.common.domain.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorPaginationTest {

  // #region compositeRoundTrip
  @Test
  void compositeRoundTripWithStringId() {
    Instant now = Instant.ofEpochMilli(1718944000123L);
    String id = UUID.randomUUID().toString();

    CursorPagination cursor = CursorPagination.of(now, id);
    String encoded = cursor.encode();

    assertNotNull(encoded);

    CursorPagination decoded = CursorPagination.decode(encoded);
    assertNotNull(decoded);
    assertEquals(now, decoded.createdAt());
    assertEquals(id, decoded.id());
  }

  @Test
  void compositeRoundTripWithUuid() {
    Instant now = Instant.ofEpochMilli(1718944000123L);
    UUID uuid = UuidV7.nextId();

    CursorPagination cursor = CursorPagination.of(now, uuid);
    String encoded = cursor.encode();

    assertNotNull(encoded);

    CursorPagination decoded = CursorPagination.decode(encoded);
    assertNotNull(decoded);
    assertEquals(now, decoded.createdAt());
    assertEquals(uuid, decoded.idAsUuid());
  }

  // #endregion

  // #region uuidV7IdOnlyRoundTrip
  @Test
  void uuidV7IdOnlyExtractsTimestampAutomatically() {
    UUID uuid = UuidV7.nextId();
    long expectedTimestamp = UuidV7.timestampOf(uuid);

    CursorPagination cursor = CursorPagination.of(uuid);
    assertEquals(Instant.ofEpochMilli(expectedTimestamp), cursor.createdAt());
    assertEquals(uuid, cursor.idAsUuid());

    String encoded = cursor.encode();
    assertNotNull(encoded);

    CursorPagination decoded = CursorPagination.decode(encoded);
    assertNotNull(decoded);
    assertEquals(uuid.toString(), decoded.id());
    assertEquals(uuid, decoded.idAsUuid());
    assertEquals(Instant.ofEpochMilli(expectedTimestamp), decoded.createdAt());
  }

  @Test
  void uuidV7StringIdOnlyExtractsTimestampAutomatically() {
    UUID uuid = UuidV7.nextId();
    long expectedTimestamp = UuidV7.timestampOf(uuid);

    CursorPagination cursor = CursorPagination.of(uuid.toString());
    assertEquals(Instant.ofEpochMilli(expectedTimestamp), cursor.createdAt());

    String encoded = cursor.encode();
    CursorPagination decoded = CursorPagination.decode(encoded);
    assertNotNull(decoded);
    assertEquals(Instant.ofEpochMilli(expectedTimestamp), decoded.createdAt());
  }

  // #endregion

  // #region decodeNullOrBlankReturnsNull
  @Test
  void decodeNullOrBlankReturnsNull() {
    assertNull(CursorPagination.decode(null));
    assertNull(CursorPagination.decode(""));
    assertNull(CursorPagination.decode("   "));
  }

  // #endregion

  // #region decodeInvalidCursorThrowsException
  @Test
  void decodeInvalidCursorThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> CursorPagination.decode("invalid-base64!"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CursorPagination.decode("bm90LWVwb2NoOmFhYQ==")); // "not-epoch:aaa"
  }
  // #endregion
}
