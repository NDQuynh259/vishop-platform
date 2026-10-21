package io.github.coreplatform.common.web.pagination;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorTest {

    // #region encodeAndDecodeRoundTrip
    @Test
    void encodeAndDecodeRoundTrip() {
        Instant now = Instant.ofEpochMilli(1718944000123L);
        String id = UUID.randomUUID().toString();

        Cursor cursor = Cursor.of(now, id);
        String encoded = cursor.encode();

        assertNotNull(encoded);

        Cursor decoded = Cursor.decode(encoded);
        assertNotNull(decoded);
        assertEquals(now, decoded.createdAt());
        assertEquals(id, decoded.id());
    }
    // #endregion

    // #region decodeNullOrBlankReturnsNull
    @Test
    void decodeNullOrBlankReturnsNull() {
        assertNull(Cursor.decode(null));
        assertNull(Cursor.decode(""));
        assertNull(Cursor.decode("   "));
    }
    // #endregion

    // #region decodeInvalidCursorThrowsException
    @Test
    void decodeInvalidCursorThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> Cursor.decode("invalid-base64!"));
        assertThrows(IllegalArgumentException.class, () -> Cursor.decode("bm9jb2xvbg==")); // "nocolon" base64
    }
    // #endregion
}
