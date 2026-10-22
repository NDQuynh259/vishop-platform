package io.github.coreplatform.common.domain.pagination;

import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque cursor pagination codec.
 *
 * <p>Supports both:
 * <ul>
 *   <li><b>UUID v7 ID-only:</b> {@code cursor = base64url(id)}. Since UUID v7 embeds
 *       a 48-bit millisecond timestamp, {@link #createdAt()} is automatically extracted.</li>
 *   <li><b>Composite legacy:</b> {@code cursor = base64url(createdAtEpochMillis:id)} for
 *       backward compatibility with existing clients and composite indexes.</li>
 * </ul>
 */
public final class CursorPagination {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Instant createdAt;
    private final String id;
    private final boolean includeTimestampInEncode;

    // #region CursorPagination
    private CursorPagination(Instant createdAt, String id, boolean includeTimestampInEncode) {
        this.createdAt = createdAt;
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.includeTimestampInEncode = includeTimestampInEncode;
    }
    // #endregion

    // #region of (composite)
    /**
     * Creates a cursor with an explicit timestamp and string identifier.
     */
    public static CursorPagination of(Instant createdAt, String id) {
        return new CursorPagination(createdAt, id, true);
    }

    /**
     * Creates a cursor with an explicit timestamp and UUID identifier.
     */
    public static CursorPagination of(Instant createdAt, UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return new CursorPagination(createdAt, id.toString(), true);
    }
    // #endregion

    // #region of (UUIDv7 / ID-only)
    /**
     * Creates an ID-only cursor from a UUID.
     * If the UUID is version 7, its embedded timestamp is automatically extracted.
     */
    public static CursorPagination of(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        Instant extractedTimestamp = null;
        if (id.version() == 7) {
            extractedTimestamp = Instant.ofEpochMilli(UuidV7.timestampOf(id));
        }
        return new CursorPagination(extractedTimestamp, id.toString(), false);
    }

    /**
     * Creates an ID-only cursor from a String.
     * If the String is a valid UUID v7, its embedded timestamp is automatically extracted.
     */
    public static CursorPagination of(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Instant extractedTimestamp = null;
        try {
            UUID parsed = UUID.fromString(id);
            if (parsed.version() == 7) {
                extractedTimestamp = Instant.ofEpochMilli(UuidV7.timestampOf(parsed));
            }
        } catch (IllegalArgumentException ignored) {
            // Non-UUID string identifier
        }
        return new CursorPagination(extractedTimestamp, id, false);
    }
    // #endregion

    // #region decode
    /**
     * Decodes an opaque base64url cursor string.
     * Accepts either {@code createdAtEpochMillis:id} or single {@code id}.
     */
    public static CursorPagination decode(String opaque) {
        if (opaque == null || opaque.isBlank()) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(DECODER.decode(opaque));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 cursor format", e);
        }

        int split = decoded.indexOf(':');
        if (split > 0) {
            long epochMillis;
            try {
                epochMillis = Long.parseLong(decoded.substring(0, split));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid cursor timestamp format", e);
            }
            String id = decoded.substring(split + 1);
            if (id.isBlank()) {
                throw new IllegalArgumentException("Cursor id cannot be empty");
            }
            return new CursorPagination(Instant.ofEpochMilli(epochMillis), id, true);
        }

        // Single ID (e.g. UUID v7)
        return of(decoded);
    }
    // #endregion

    // #region encode
    /**
     * Encodes this cursor to an opaque base64url string.
     */
    public String encode() {
        String payload;
        if (includeTimestampInEncode && createdAt != null) {
            payload = createdAt.toEpochMilli() + ":" + id;
        } else {
            payload = id;
        }
        return ENCODER.encodeToString(payload.getBytes());
    }
    // #endregion

    // #region getters
    public Instant createdAt() {
        return createdAt;
    }

    public String id() {
        return id;
    }

    /**
     * Parses the cursor ID as a {@link UUID}.
     */
    public UUID idAsUuid() {
        return UUID.fromString(id);
    }
    // #endregion
}
