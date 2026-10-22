package com.vishop.identity.domain.exception;

import io.github.coreplatform.common.domain.exception.ConflictException;

/**
 * Domain Exception thrown when attempting to register with an already existing email.
 */
public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException(String email) {
        super("Email address is already registered: " + email);
    }
}
