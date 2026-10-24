package io.github.coreplatform.common.domain.exception;

/**
 * Thrown when an unauthenticated request attempts to access a protected operation. Typically mapped
 * to HTTP 401 Unauthorized.
 */
public class UnauthorizedException extends DomainException {

  // #region UnauthorizedException
  public UnauthorizedException() {
    super("unauthorized", "Authentication is required to access this resource");
  }

  // #endregion

  // #region UnauthorizedException
  public UnauthorizedException(String message) {
    super("unauthorized", message);
  }

  // #endregion

  // #region UnauthorizedException
  public UnauthorizedException(String code, String message) {
    super(code, message);
  }
  // #endregion
}
