package io.github.coreplatform.common.domain.pagination;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CursorParamsTest {

    // #region defaultValues
    @Test
    void defaultValuesWhenZeroOrNegative() {
        CursorParams params = CursorParams.of(null, 0);
        assertEquals(CursorParams.DEFAULT_LIMIT, params.limit());
        assertNull(params.cursor());
        assertNull(params.decodeCursor());

        CursorParams negative = CursorParams.of("abc", -10);
        assertEquals(CursorParams.DEFAULT_LIMIT, negative.limit());
        assertEquals("abc", negative.cursor());
    }
    // #endregion

    // #region limitClamping
    @Test
    void limitClamping() {
        CursorParams params = CursorParams.of(null, 500);
        assertEquals(CursorParams.MAX_LIMIT, params.limit());
    }
    // #endregion

    // #region decodeCursor
    @Test
    void decodeCursor() {
        Instant now = Instant.ofEpochMilli(1718944000123L);
        UUID id = UUID.randomUUID();
        String encoded = CursorPagination.of(now, id).encode();

        CursorParams params = CursorParams.of(encoded, 25);
        assertEquals(25, params.limit());
        assertEquals(encoded, params.cursor());

        CursorPagination decoded = params.decodeCursor();
        assertNotNull(decoded);
        assertEquals(now, decoded.createdAt());
        assertEquals(id, decoded.idAsUuid());
    }
    // #endregion
}
