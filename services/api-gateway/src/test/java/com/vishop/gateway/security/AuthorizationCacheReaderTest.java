package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import redis.embedded.RedisServer;

class AuthorizationCacheReaderTest {
  RedisServer server;
  LettuceConnectionFactory connection;
  HttpServer iam;
  final ObjectMapper json = new ObjectMapper();
  final String secret = "test-context-secret-with-at-least-thirty-two-bytes";
  final AtomicReference<AuthorizationSnapshot> current = new AtomicReference<>();
  final AtomicInteger calls = new AtomicInteger();
  StringRedisTemplate redis;
  AuthorizationCacheReader reader;

  @BeforeEach
  void start() throws Exception {
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
    iam = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var codec = new GatewayContextCodec(json, secret, "iam-service");
    iam.createContext(
        "/internal/authorization",
        request -> {
          try {
            codec.verify(
                request.getRequestHeaders().getFirst(GatewayContextCodec.HEADER),
                "GET",
                request.getRequestURI().toASCIIString());
            calls.incrementAndGet();
            var state = current.get();
            if (state == null) {
              request.sendResponseHeaders(404, -1);
              return;
            }
            var body = json.writeValueAsBytes(state);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, body.length);
            request.getResponseBody().write(body);
          } catch (Exception invalid) {
            request.sendResponseHeaders(401, -1);
          } finally {
            request.close();
          }
        });
    iam.start();
    var reactive = new ReactiveStringRedisTemplate(connection);
    reactive.opsForValue().get("test:connection-ready").block(Duration.ofSeconds(10));
    reader =
        new AuthorizationCacheReader(
            reactive,
            json,
            new GatewayContextSigners(json, secret, secret, secret),
            "http://127.0.0.1:" + iam.getAddress().getPort(),
            "test:",
            30);
  }

  @AfterEach
  void stop() throws Exception {
    if (iam != null) iam.stop(0);
    if (connection != null) connection.destroy();
    if (server != null) server.stop();
  }

  @Test
  void coldMissesShareAuthenticatedIamLoadAndRevokedRoleReplacesCachedPermissions() {
    UUID tenant = UUID.randomUUID(), role = UUID.randomUUID();
    String key = AuthorizationSnapshot.role(tenant, role);
    current.set(
        new AuthorizationSnapshot(
            key, "1", "ACTIVE", null, role, "WAREHOUSE", Set.of("inventory.adjust"), 0));
    var results =
        Flux.range(0, 20).flatMap(i -> reader.get(key)).collectList().block(Duration.ofSeconds(5));
    assertThat(results).hasSize(20);
    assertThat(calls.get()).isEqualTo(1);
    reader.get(key).block();
    assertThat(calls.get()).isEqualTo(1);
    current.set(
        new AuthorizationSnapshot(
            key, "2", "ACTIVE", null, role, "WAREHOUSE", Set.of("inventory.view"), 0));
    redis.opsForHash().put("test:" + key, "checkedAt", "1");
    assertThat(reader.get(key).block().permissions()).containsExactly("inventory.view");
    assertThat(calls.get()).isEqualTo(2);
    iam.stop(0);
    iam = null;
    assertThat(reader.get(key).block().permissions()).containsExactly("inventory.view");
    redis.opsForHash().put("test:" + key, "checkedAt", "1");
    assertThatThrownBy(() -> reader.get(key).block(Duration.ofSeconds(5)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void missingRoleDeniesInsteadOfCreatingAnActiveDefault() {
    assertThatThrownBy(
            () ->
                reader
                    .get(AuthorizationSnapshot.role(UUID.randomUUID(), UUID.randomUUID()))
                    .block(Duration.ofSeconds(5)))
        .isInstanceOf(AuthenticationFailure.class);
  }

  @Test
  void explicitRefreshConfirmsStateBeforeTheNormalFreshnessDeadline() {
    String key = AuthorizationSnapshot.user(UUID.randomUUID());
    current.set(new AuthorizationSnapshot(key, "1", "ACTIVE", "USER", null, null, Set.of(), 1));
    assertThat(reader.get(key).block().tokenVersion()).isEqualTo(1);
    current.set(new AuthorizationSnapshot(key, "2", "ACTIVE", "USER", null, null, Set.of(), 2));
    assertThat(reader.get(key).block().tokenVersion()).isEqualTo(1);
    assertThat(reader.refresh(key).block().tokenVersion()).isEqualTo(2);
    assertThat(calls.get()).isEqualTo(2);
  }
}
