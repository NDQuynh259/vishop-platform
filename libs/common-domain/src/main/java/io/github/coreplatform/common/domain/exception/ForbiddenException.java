package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when an authenticated principal does not have permission for the requested resource.
 * Typically mapped to HTTP 403 Forbidden.
 */
public class ForbiddenException extends DomainException {

    // #region ForbiddenException
    public ForbiddenException() {
        super("forbidden", "Access to this resource is denied");
    }
    // #endregion

    // #region ForbiddenException
    public ForbiddenException(String message) {
        super("forbidden", message);
    }
    // #endregion

    // #region ForbiddenException
    public ForbiddenException(String code, String message) {
        super(code, message);
    }
    // #endregion
}
