package io.github.coreplatform.common.cache;

import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Nonblocking cache reads. Missing values complete empty; batch reads preserve key order and
 * misses.
 */
public interface ReactiveCacheReader {
  <T> Mono<T> get(String key, Class<T> type);

  <T> Mono<List<Optional<T>>> getAll(List<String> keys, Class<T> type);

  <T> Mono<T> getHashValue(String key, String field, Class<T> type);
}
