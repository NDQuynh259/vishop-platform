package com.vishop.identity.domain.exception;

import io.github.coreplatform.common.domain.exception.UnauthorizedException;

/**
 * Domain Exception thrown when authentication fails due to mismatched credentials.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
