package com.vishop.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void shouldGenerateValidUuidV7() {
        UUID id = UuidV7.nextId();
        assertNotNull(id);
        assertEquals(7, id.version(), "Must be UUID version 7");
        assertEquals(2, id.variant(), "Must be RFC 9562 IETF variant (2)");
    }

    @Test
    void shouldExtractAccurateTimestamp() {
        long now = System.currentTimeMillis();
        UUID id = UuidV7.fromTimestamp(now);
        long extracted = UuidV7.timestampOf(id);
        assertEquals(now, extracted);
    }

    @Test
    void shouldMaintainMonotonicTimeOrder() {
        UUID id1 = UuidV7.nextId();
        UUID id2 = UuidV7.nextId();
        assertTrue(id1.compareTo(id2) <= 0 || UuidV7.timestampOf(id1) <= UuidV7.timestampOf(id2));
    }
}
