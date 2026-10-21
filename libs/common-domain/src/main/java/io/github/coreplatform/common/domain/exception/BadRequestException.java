package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when a request precondition or semantic input check fails.
 * Typically mapped to HTTP 400 Bad Request.
 */
public class BadRequestException extends DomainException {

    // #region BadRequestException
    public BadRequestException(String message) {
        super("bad_request", message);
    }
    // #endregion

    // #region BadRequestException
    public BadRequestException(String code, String message) {
        super(code, message);
    }
    // #endregion
}
