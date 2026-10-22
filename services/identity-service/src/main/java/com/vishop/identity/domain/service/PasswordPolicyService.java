package com.vishop.identity.domain.service;

import io.github.coreplatform.common.domain.exception.DomainException;
import java.util.regex.Pattern;

/**
 * Domain Service enforcing password strength policies.
 * Rules:
 * - Minimum 8 characters, maximum 128 characters
 * - At least one uppercase letter [A-Z]
 * - At least one lowercase letter [a-z]
 * - At least one digit [0-9]
 * - At least one special symbol [@$!%*?&#^()_-]
 */
public class PasswordPolicyService {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private static final Pattern UPPER_CASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWER_CASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new DomainException(
                    "password_too_short",
                    "Password must be at least " + MIN_LENGTH + " characters long"
            );
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new DomainException(
                    "password_too_long",
                    "Password must not exceed " + MAX_LENGTH + " characters"
            );
        }
        if (!UPPER_CASE_PATTERN.matcher(rawPassword).find()) {
            throw new DomainException(
                    "password_missing_uppercase",
                    "Password must contain at least one uppercase letter"
            );
        }
        if (!LOWER_CASE_PATTERN.matcher(rawPassword).find()) {
            throw new DomainException(
                    "password_missing_lowercase",
                    "Password must contain at least one lowercase letter"
            );
        }
        if (!DIGIT_PATTERN.matcher(rawPassword).find()) {
            throw new DomainException(
                    "password_missing_digit",
                    "Password must contain at least one number"
            );
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(rawPassword).find()) {
            throw new DomainException(
                    "password_missing_special_char",
                    "Password must contain at least one special character"
            );
        }
    }
}
