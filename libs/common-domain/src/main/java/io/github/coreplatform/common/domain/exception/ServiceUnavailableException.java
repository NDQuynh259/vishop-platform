package io.github.coreplatform.common.domain.exception;

/** A service or required dependency is temporarily unavailable. */
public class ServiceUnavailableException extends RetryableException {
  public ServiceUnavailableException(String message) {
    this(message, null, null);
  }

  public ServiceUnavailableException(String message, Long retryAfterSeconds) {
    this(message, retryAfterSeconds, null);
  }

  public ServiceUnavailableException(String message, Long retryAfterSeconds, Throwable cause) {
    super("service_unavailable", message, retryAfterSeconds, cause);
  }
}
