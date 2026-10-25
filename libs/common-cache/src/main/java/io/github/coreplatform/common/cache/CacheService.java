package io.github.coreplatform.common.cache;

import java.time.Duration;
import java.util.Optional;

/** General cache operations. Callers choose keys, TTL and when to cache or invalidate data. */
public interface CacheService {
  <T> Optional<T> get(String key, Class<T> type);

  /** Stores a non-null string or JSON value; zero TTL means persistent. */
  void put(String key, Object value, Duration ttl);

  boolean evict(String key);

  boolean exists(String key);

  /** Changes an existing entry's TTL; zero removes expiry. Returns false when the key is absent. */
  boolean expire(String key, Duration ttl);
}
