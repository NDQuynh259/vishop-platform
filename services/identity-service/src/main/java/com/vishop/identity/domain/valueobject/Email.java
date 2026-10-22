package com.vishop.identity.domain.valueobject;

import io.github.coreplatform.common.domain.ValueObject;
import io.github.coreplatform.common.domain.exception.DomainException;
import java.util.regex.Pattern;

/**
 * Value Object representing a validated, normalized email address.
 */
public record Email(String value) implements ValueObject {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainException("invalid_email", "Email address cannot be empty");
        }
        String normalized = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("invalid_email_format", "Invalid email format: " + value);
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
