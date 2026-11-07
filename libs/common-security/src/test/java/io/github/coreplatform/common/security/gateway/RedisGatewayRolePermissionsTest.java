package io.github.coreplatform.common.security.gateway;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
import io.github.coreplatform.common.cache.CacheKeyBuilder;
import io.github.coreplatform.common.cache.redis.VersionedAuthorizationCache;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import redis.embedded.RedisServer;

class RedisGatewayRolePermissionsTest {
  static RedisServer server;
  static LettuceConnectionFactory connection;
  static StringRedisTemplate redis;
  final ObjectMapper json = new ObjectMapper();
  final UUID tenant = UUID.randomUUID(), role = UUID.randomUUID();
  final String key = AuthorizationCacheKeys.role(tenant, role);
  final String roleKey = "CUSTOM:" + role;
  GatewayRolePermissions reader;

  @BeforeAll
  static void start() throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    server =
        RedisServer.newRedisServer()
            .port(port)
            .setting("bind 127.0.0.1")
            .setting("maxmemory 64M")
            .build();
    server.start();
    connection = new LettuceConnectionFactory("127.0.0.1", port);
    connection.afterPropertiesSet();
    connection.start();
    redis = new StringRedisTemplate(connection);
  }

  @AfterAll
  static void stop() throws Exception {
    if (connection != null) connection.destroy();
    if (server != null) server.stop();
  }

  @BeforeEach
  void reader() {
    reader = new RedisGatewayRolePermissions(redis, json, new CacheKeyBuilder("headers-test:"), 30);
  }

  void write(
      String revision, AuthorizationStatus status, String payloadKey, Set<String> permissions)
      throws Exception {
    var state =
        new AuthorizationSnapshot(
            payloadKey, revision, status, null, role, roleKey, permissions, 0);
    redis.execute(
        new DefaultRedisScript<Long>(VersionedAuthorizationCache.WRITE, Long.class),
        List.of("headers-test:" + key),
        revision,
        json.writeValueAsString(state),
        "");
  }

  @Test
  void editsApplyWithoutChangingIdentityAndOldRevisionsCannotRestoreGrants() throws Exception {
    write("1", AuthorizationStatus.ACTIVE, key, Set.of("inventory.view"));
    assertThat(reader.resolve(tenant, role).getPermissions()).containsExactly("inventory.view");
    assertThat(reader.resolve(tenant, role).getRoleKey()).isEqualTo(roleKey);
    write("2", AuthorizationStatus.ACTIVE, key, Set.of("inventory.edit"));
    assertThat(reader.resolve(tenant, role).getPermissions()).containsExactly("inventory.edit");
    write("1", AuthorizationStatus.ACTIVE, key, Set.of("system.admin"));
    assertThat(reader.resolve(tenant, role).getPermissions()).containsExactly("inventory.edit");
    write("3", AuthorizationStatus.INACTIVE, key, Set.of());
    assertThatThrownBy(() -> reader.resolve(tenant, role).getPermissions())
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void missingStaleAndForeignTenantDataAreRejected() throws Exception {
    assertThatThrownBy(() -> reader.resolve(tenant, role).getPermissions())
        .isInstanceOf(IllegalStateException.class);
    write("1", AuthorizationStatus.ACTIVE, key, Set.of("inventory.view"));
    redis.opsForHash().put("headers-test:" + key, "checkedAt", "1");
    assertThatThrownBy(() -> reader.resolve(tenant, role).getPermissions())
        .isInstanceOf(IllegalStateException.class);
    write(
        "2",
        AuthorizationStatus.ACTIVE,
        AuthorizationCacheKeys.role(UUID.randomUUID(), role),
        Set.of("system.admin"));
    assertThatThrownBy(() -> reader.resolve(tenant, role).getPermissions())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void requiresConfiguredPrefixAndMatchingRoleIdentity() throws Exception {
    write("1", AuthorizationStatus.ACTIVE, key, Set.of("inventory.view"));
    var wrongPrefix =
        new RedisGatewayRolePermissions(redis, json, new CacheKeyBuilder("other:"), 30);
    assertThatThrownBy(() -> wrongPrefix.resolve(tenant, role).getPermissions())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> reader.resolve(tenant, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(reader.resolve(tenant, role).getPermissions()).containsExactly("inventory.view");
  }
}
