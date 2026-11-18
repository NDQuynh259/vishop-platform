package io.github.coreplatform.common.domain.exception;

/** A temporary failure with an optional retry delay, independent of the HTTP adapter. */
public abstract class RetryableException extends DomainException {
  private final Long retryAfterSeconds;

  protected RetryableException(
      String code, String message, Long retryAfterSeconds, Throwable cause) {
    super(code, message, cause);
    if (retryAfterSeconds != null && retryAfterSeconds < 0) {
      throw new IllegalArgumentException("Retry delay must not be negative");
    }
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
