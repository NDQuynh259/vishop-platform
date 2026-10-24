package io.github.coreplatform.common.domain.exception;

/** Base runtime exception for all domain layer invariant and business rule failures. */
public class DomainException extends RuntimeException {

  private final String code;

  // #region DomainException
  public DomainException(String message) {
    this("domain_error", message);
  }

  // #endregion

  // #region DomainException
  public DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  // #endregion

  // #region DomainException
  public DomainException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  // #endregion

  // #region getCode
  public String getCode() {
    return code;
  }
  // #endregion
}
