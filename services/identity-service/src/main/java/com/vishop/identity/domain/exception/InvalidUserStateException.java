package com.vishop.identity.domain.exception;

import io.github.coreplatform.common.domain.exception.DomainException;

/**
 * Domain Exception thrown when an action cannot be performed due to current user state.
 */
public class InvalidUserStateException extends DomainException {

    public InvalidUserStateException(String message) {
        super("invalid_user_state", message);
    }
}
