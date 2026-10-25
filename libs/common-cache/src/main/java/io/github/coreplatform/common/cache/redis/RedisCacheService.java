package io.github.coreplatform.common.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.CacheService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Blocking Redis cache with string/JSON values and a configurable namespace for caller-supplied
 * keys.
 */
public final class RedisCacheService implements CacheService {
  private static final DefaultRedisScript<Boolean> PERSIST =
      new DefaultRedisScript<>(
          "if redis.call('EXISTS',KEYS[1])==0 then return 0 end; redis.call('PERSIST',KEYS[1]); return 1",
          Boolean.class);
  private final StringRedisTemplate redis;
  private final CacheValueCodec codec;
  private final String keyPrefix;

  public RedisCacheService(StringRedisTemplate redis, ObjectMapper json, String keyPrefix) {
    this.redis = Objects.requireNonNull(redis, "redis");
    this.codec = new CacheValueCodec(json);
    this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    Objects.requireNonNull(type, "type");
    String value = redis.opsForValue().get(redisKey(key));
    if (value == null) return Optional.empty();
    return Optional.of(codec.decode(value, type));
  }

  @Override
  public void put(String key, Object value, Duration ttl) {
    String redisKey = redisKey(key);
    validateTtl(ttl);
    String encoded = codec.encode(value);
    if (ttl.isZero()) redis.opsForValue().set(redisKey, encoded);
    else redis.opsForValue().set(redisKey, encoded, ttl);
  }

  @Override
  public boolean evict(String key) {
    return Boolean.TRUE.equals(redis.delete(redisKey(key)));
  }

  @Override
  public boolean exists(String key) {
    return Boolean.TRUE.equals(redis.hasKey(redisKey(key)));
  }

  @Override
  public boolean expire(String key, Duration ttl) {
    String redisKey = redisKey(key);
    validateTtl(ttl);
    if (ttl.isZero()) {
      // PERSIST returns false for an already persistent key, so check existence
      // atomically.
      return Boolean.TRUE.equals(redis.execute(PERSIST, List.of(redisKey)));
    }
    return Boolean.TRUE.equals(redis.expire(redisKey, ttl));
  }

  /** Executes caller-owned Lua atomically. Keys receive this cache's prefix; arguments stay raw. */
  public <T> T execute(RedisScript<T> script, List<String> keys, String... arguments) {
    Objects.requireNonNull(script, "script");
    return Objects.requireNonNull(
        redis.execute(script, keys.stream().map(this::redisKey).toList(), (Object[]) arguments),
        "Redis did not return a script result");
  }

  private String redisKey(String key) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
    return keyPrefix + key;
  }

  private static void validateTtl(Duration ttl) {
    Objects.requireNonNull(ttl, "ttl");
    if (ttl.isNegative() || (!ttl.isZero() && ttl.toMillis() == 0))
      throw new IllegalArgumentException("ttl must be zero or at least one millisecond");
  }
}
