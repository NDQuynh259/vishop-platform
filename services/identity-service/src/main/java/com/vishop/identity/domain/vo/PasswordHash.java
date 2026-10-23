package com.vishop.identity.domain.valueobjects;

import io.github.coreplatform.common.domain.ValueObject;
import java.util.Objects;

/**
 * Value Object representing a hashed password (e.g. BCrypt hash).
 * Obfuscates toString() to prevent leaking hash in logs.
 */
public record PasswordHash(String value) implements ValueObject {

    public PasswordHash {
        Objects.requireNonNull(value, "Password hash cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be empty");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    @Override
    public String toString() {
        return "********";
    }
}
