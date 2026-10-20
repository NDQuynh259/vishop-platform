package io.github.coreplatform.common.web;

import java.util.Base64;
import java.util.Objects;
import java.time.Instant;

/**
 * Opaque cursor codec.
 *
 * <p>cursor = base64url(createdAtEpochMillis:id), matching the API contract:
 * ordered by createdAt desc, id desc.
 */
public final class Cursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Instant createdAt;
    private final String id;

    private Cursor(Instant createdAt, String id) {
        this.createdAt = createdAt;
        this.id = Objects.requireNonNull(id);
    }

    public static Cursor of(Instant createdAt, String id) {
        return new Cursor(createdAt, id);
    }

    public static Cursor decode(String opaque) {
        if (opaque == null || opaque.isBlank()) {
            return null;
        }
        String decoded = new String(DECODER.decode(opaque));
        int split = decoded.indexOf(':');
        if (split <= 0) {
            throw new IllegalArgumentException("Invalid cursor format");
        }
        long epochMillis = Long.parseLong(decoded.substring(0, split));
        String id = decoded.substring(split + 1);
        return new Cursor(Instant.ofEpochMilli(epochMillis), id);
    }

    public String encode() {
        return ENCODER.encodeToString(
                (createdAt.toEpochMilli() + ":" + id).getBytes());
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String id() {
        return id;
    }
}
