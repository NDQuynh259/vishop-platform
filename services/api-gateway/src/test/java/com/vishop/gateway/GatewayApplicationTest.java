package com.vishop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "vishop.security.jwt.secret=test-jwt-secret-with-at-least-thirty-two-bytes",
      "management.health.redis.enabled=false"
    })
class GatewayApplicationTest {
  private static final java.util.concurrent.atomic.AtomicReference<com.sun.net.httpserver.Headers>
      FORWARDED = new java.util.concurrent.atomic.AtomicReference<>();
  private static final com.sun.net.httpserver.HttpServer BACKEND = startBackend();

  private static com.sun.net.httpserver.HttpServer startBackend() {
    try {
      var server =
          com.sun.net.httpserver.HttpServer.create(
              new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/api/v1/audit/probe",
          exchange -> {
            FORWARDED.set(exchange.getRequestHeaders());
            byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void routing(DynamicPropertyRegistry properties) {
    properties.add("IAM_SERVICE_URL", () -> "http://127.0.0.1:1");
    properties.add("AUDIT_SERVICE_URL", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
  }

  @AfterAll
  static void stop() throws Exception {
    BACKEND.stop(0);
  }

  @Autowired JwtTokenProvider tokens;

  @LocalServerPort private int port;

  @Autowired private WebTestClient webTestClient;

  @Test
  void businessRequestWorksWhileIamIsUnavailable() {
    var user = java.util.UUID.randomUUID();
    var tenant = java.util.UUID.randomUUID();
    String jwt =
        tokens.generateIamAccessToken(
            user, tenant, "USER", java.util.List.of("OWNER"), java.util.Set.of("audit_log.view"));
    webTestClient
        .get()
        .uri("/api/v1/audit/probe")
        .header("Authorization", "Bearer " + jwt)
        .header("X-User-Id", "spoofed")
        .header("X-User-Permissions", "system.admin")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
    assertThat(FORWARDED.get().getFirst("Authorization")).isEqualTo("Bearer " + jwt);
    assertThat(FORWARDED.get().getFirst("X-User-Id")).isEqualTo(user.toString());
    assertThat(FORWARDED.get().getFirst("X-User-Permissions")).isNull();
  }

  @Test
  @DisplayName("Application context loads successfully")
  void contextLoads() {
    assertThat(port).isPositive();
  }

  @Test
  @DisplayName("Actuator health endpoint returns UP")
  void healthEndpointShouldBeAccessible() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  @DisplayName("Should return 401 when accessing protected route without authentication")
  void shouldReturn401WhenUnauthenticated() {
    webTestClient
        .get()
        .uri("/api/v1/tenants")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/errors/unauthorized");
  }

  @Test
  @DisplayName("Should return 400 when authenticated user supplies malformed X-Tenant-Id")
  void shouldReturn400OnInvalidTenantId() {
    String token = "test-token";

    webTestClient
        .get()
        .uri("/api/v1/tenants")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-Id", "not-a-valid-uuid")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType("application/problem+json")
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/errors/invalid-tenant-id");
  }

  @Test
  @DisplayName("CORS preflight request should return allowed headers")
  void corsPreflightShouldSucceed() {
    webTestClient
        .options()
        .uri("/api/v1/tenants")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
  }
}
