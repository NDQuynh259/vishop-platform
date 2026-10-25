package io.github.coreplatform.common.cache;

/** Cache data cannot be encoded or decoded; the failure is not treated as a cache miss. */
public class CacheSerializationException extends RuntimeException {
  public CacheSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
