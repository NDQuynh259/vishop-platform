package com.vishop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coreplatform.common.security.constants.AuthCookies;
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
  private static final java.util.concurrent.atomic.AtomicReference<com.sun.net.httpserver.Headers>
      AUTH_FORWARDED = new java.util.concurrent.atomic.AtomicReference<>();
  private static final java.util.concurrent.atomic.AtomicInteger IAM_LOOKUPS =
      new java.util.concurrent.atomic.AtomicInteger();
  private static final com.sun.net.httpserver.HttpServer BACKEND = startBackend();

  private static com.sun.net.httpserver.HttpServer startBackend() {
    try {
      var server =
          com.sun.net.httpserver.HttpServer.create(
              new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/internal/auth/introspect",
          exchange -> {
            IAM_LOOKUPS.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
          });
      server.createContext(
          "/api/v1/auth/refresh",
          exchange -> {
            AUTH_FORWARDED.set(exchange.getRequestHeaders());
            // IAM also emits CORS headers; gateway must return each one only once.
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null) {
              exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
              exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
            }
            exchange
                .getResponseHeaders()
                .add(
                    "Set-Cookie",
                    AuthCookies.ACCESS_TOKEN + "=new-access; Path=/; HttpOnly; SameSite=Lax");
            exchange
                .getResponseHeaders()
                .add(
                    "Set-Cookie",
                    AuthCookies.REFRESH_TOKEN
                        + "=new-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Lax");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body =
                "{\"user\":{\"email\":\"cookie@example.com\"},\"tenantId\":null,\"roles\":[],\"permissions\":[]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.createContext(
          "/v3/api-docs",
          exchange -> {
            if (!"/v3/api-docs".equals(exchange.getRequestURI().getPath())) {
              exchange.sendResponseHeaders(404, -1);
              exchange.close();
              return;
            }
            byte[] body =
                "{\"openapi\":\"3.0.1\",\"info\":{\"title\":\"Audit test API\",\"version\":\"1\"},\"paths\":{}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
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
    properties.add("IAM_SERVICE_URL", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
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
  void exposesOpenApiSpecificationWithoutAuthentication() {
    webTestClient
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.openapi")
        .value(org.hamcrest.Matchers.startsWith("3."));
  }

  @Test
  void routesDownstreamOpenApiSpecificationWithoutAuthentication() {
    webTestClient
        .get()
        .uri("/v3/api-docs/audit-service")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.openapi")
        .isEqualTo("3.0.1")
        .jsonPath("$.info.title")
        .isEqualTo("Audit test API");
  }

  @Test
  void businessCookieRequestWorksWithoutIamIntrospection() {
    int previousLookups = IAM_LOOKUPS.get();
    var user = java.util.UUID.randomUUID();
    var tenant = java.util.UUID.randomUUID();
    String jwt =
        tokens.generateIamAccessToken(
            user, tenant, "USER", java.util.List.of("OWNER"), java.util.Set.of("audit_log.view"));
    webTestClient
        .get()
        .uri("/api/v1/audit/probe")
        .cookie(AuthCookies.ACCESS_TOKEN, jwt)
        .cookie(AuthCookies.REFRESH_TOKEN, "must-not-reach-audit")
        .header("X-User-Id", "spoofed")
        .header("X-User-Permissions", "system.admin")
        .header("X-Tenant-Id", "not-a-valid-uuid")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
    assertThat(FORWARDED.get().getFirst("Authorization")).isEqualTo("Bearer " + jwt);
    assertThat(FORWARDED.get().getFirst("Cookie")).isNull();
    assertThat(IAM_LOOKUPS.get()).isEqualTo(previousLookups);
    assertThat(FORWARDED.get().getFirst("X-User-Id")).isEqualTo(user.toString());
    assertThat(FORWARDED.get().getFirst("X-User-Permissions")).isNull();
    assertThat(FORWARDED.get().getFirst("X-Tenant-Id")).isEqualTo(tenant.toString());
  }

  @Test
  void refreshCookiesAreForwardedAndBothSetCookieHeadersReachTheBrowser() {
    webTestClient
        .post()
        .uri("/api/v1/auth/refresh")
        .header("Origin", "http://localhost:3000")
        .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
        .cookie(AuthCookies.ACCESS_TOKEN, "expired-access")
        .cookie(AuthCookies.REFRESH_TOKEN, "valid-refresh")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
        .expectHeader()
        .valueEquals("Access-Control-Allow-Credentials", "true")
        .expectCookie()
        .valueEquals(AuthCookies.ACCESS_TOKEN, "new-access")
        .expectCookie()
        .httpOnly(AuthCookies.ACCESS_TOKEN, true)
        .expectCookie()
        .valueEquals(AuthCookies.REFRESH_TOKEN, "new-refresh")
        .expectCookie()
        .httpOnly(AuthCookies.REFRESH_TOKEN, true)
        .expectBody()
        .jsonPath("$.accessToken")
        .doesNotExist()
        .jsonPath("$.refreshToken")
        .doesNotExist();
    assertThat(AUTH_FORWARDED.get().getFirst("Cookie"))
        .isEqualTo(AuthCookies.REFRESH_TOKEN + "=valid-refresh");
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
  @DisplayName("Should reject invalid token even when client supplies malformed X-Tenant-Id")
  void shouldReturn401OnInvalidTokenWithMalformedTenantHeader() {
    String token = "test-token";

    webTestClient
        .get()
        .uri("/api/v1/tenants")
        .header("Authorization", "Bearer " + token)
        .header("X-Tenant-Id", "not-a-valid-uuid")
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
  @DisplayName("CORS preflight request should return allowed headers")
  void corsPreflightShouldSucceed() {
    webTestClient
        .options()
        .uri("/api/v1/tenants")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "content-type,x-requested-with")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Credentials", "true")
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
  }
}
