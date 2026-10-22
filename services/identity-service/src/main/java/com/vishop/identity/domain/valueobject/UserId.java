package com.vishop.identity.domain.valueobject;

import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a unique User identifier.
 */
public record UserId(UUID value) implements ValueObject {

    public UserId {
        Objects.requireNonNull(value, "UserId value cannot be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    public static UserId random() {
        return new UserId(UuidV7.nextId());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
