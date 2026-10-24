package com.vishop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "vishop.iam.internal-secret=test-internal-secret-at-least-thirty-two-bytes")
class GatewayApplicationTest {

  @LocalServerPort private int port;

  @Autowired private WebTestClient webTestClient;

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
