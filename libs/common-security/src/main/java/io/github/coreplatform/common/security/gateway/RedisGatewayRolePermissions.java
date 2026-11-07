package io.github.coreplatform.common.security.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
import io.github.coreplatform.common.cache.CacheKeyBuilder;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Reads tenant role metadata and permissions refreshed by the gateway before forwarding. */
public final class RedisGatewayRolePermissions implements GatewayRolePermissions {
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final CacheKeyBuilder keys;
  private final long maxAge;
  private static final DefaultRedisScript<String> READ =
      new DefaultRedisScript<>(VersionedAuthorizationCache.READ, String.class);

  public RedisGatewayRolePermissions(
      StringRedisTemplate redis, ObjectMapper json, CacheKeyBuilder keys, long maxAge) {
    if (maxAge < 1 || maxAge > 300)
      throw new IllegalArgumentException("Invalid authorization freshness");
    this.redis = redis;
    this.json = json;
    this.keys = keys;
    this.maxAge = maxAge;
  }

  public Grant resolve(UUID tenantId, UUID roleId) {
    var state = snapshot(AuthorizationCacheKeys.role(tenantId, roleId));
    if (state.status() != AuthorizationStatus.ACTIVE)
      throw new ForbiddenException("role-inactive", "Role is not active");
    if (!roleId.equals(state.roleId())
        || state.roleKey() == null
        || state.roleKey().isBlank()
        || state.permissions().stream().anyMatch(p -> p == null || p.isBlank())) {
      throw new IllegalStateException("Invalid cached role state");
    }
    return new Grant(state.roleKey(), state.permissions());
  }

  private AuthorizationSnapshot snapshot(String key) {
    String payload = redis.execute(READ, List.of(keys.withPrefix(key)), Long.toString(maxAge));
    if (payload == null) throw new IllegalStateException("Authorization state is missing or stale");
    AuthorizationSnapshot state;
    try {
      state = json.readValue(payload, AuthorizationSnapshot.class);
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid authorization state", ex);
    }
    if (state == null || !key.equals(state.key()))
      throw new IllegalStateException("Authorization scope mismatch");
    return state;
  }
}
