package io.github.coreplatform.common.domain.exception;

/** A request exceeds a rate or concurrent resource limit. */
public class TooManyRequestsException extends RetryableException {
  public TooManyRequestsException(String message) {
    this(message, null);
  }

  public TooManyRequestsException(String message, Long retryAfterSeconds) {
    super("too_many_requests", message, retryAfterSeconds, null);
  }
}
