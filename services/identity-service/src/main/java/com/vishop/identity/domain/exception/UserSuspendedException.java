package com.vishop.identity.domain.exception;

import io.github.coreplatform.common.domain.exception.ForbiddenException;

/**
 * Domain Exception thrown when authentication or action is attempted on a suspended user.
 */
public class UserSuspendedException extends ForbiddenException {

    public UserSuspendedException(String message) {
        super(message);
    }
}
