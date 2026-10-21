package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when a unique constraint or concurrency conflict occurs.
 * Typically mapped to HTTP 409 Conflict.
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
