package io.github.coreplatform.common.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.ReactiveCacheReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/** Reads the same string/JSON format as RedisCacheService without blocking reactive threads. */
public final class ReactiveRedisCacheReader implements ReactiveCacheReader {
  private final ReactiveStringRedisTemplate redis;
  private final CacheValueCodec codec;
  private final String keyPrefix;

  public ReactiveRedisCacheReader(
      ReactiveStringRedisTemplate redis, ObjectMapper json, String keyPrefix) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.codec = new CacheValueCodec(json);
    this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
  }

  @Override
  public <T> Mono<T> get(String key, Class<T> type) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(type, "type");
          return redis.opsForValue().get(redisKey(key)).map(value -> codec.decode(value, type));
        });
  }

  @Override
  public <T> Mono<List<Optional<T>>> getAll(List<String> keys, Class<T> type) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(type, "type");
          List<String> redisKeys = keys.stream().map(this::redisKey).toList();
          if (redisKeys.isEmpty()) return Mono.just(List.of());
          return redis
              .opsForValue()
              .multiGet(redisKeys)
              .map(
                  values ->
                      values.stream()
                          .map(
                              value ->
                                  Optional.ofNullable(value).map(text -> codec.decode(text, type)))
                          .toList());
        });
  }

  @Override
  public <T> Mono<T> getHashValue(String key, String field, Class<T> type) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(type, "type");
          if (field == null || field.isBlank())
            throw new IllegalArgumentException("field must not be blank");
          return redis
              .<String, String>opsForHash()
              .get(redisKey(key), field)
              .map(value -> codec.decode(value, type));
        });
  }

  private String redisKey(String key) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
    return keyPrefix + key;
  }
}
