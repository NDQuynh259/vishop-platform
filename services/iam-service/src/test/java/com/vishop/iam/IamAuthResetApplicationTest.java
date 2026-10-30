package com.vishop.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vishop.iam.modules.identity.presentation.rest.AuthController;
import io.github.coreplatform.common.security.gateway.GatewayContextCodec;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Exercises the real application: no auth implementation and no accidentally public business API.
 */
@SpringBootTest(properties = {"vishop.outbox.enabled=false", "management.tracing.enabled=false"})
@AutoConfigureMockMvc
class IamAuthResetApplicationTest {
  private static final EmbeddedPostgres POSTGRES = start();

  private static EmbeddedPostgres start() {
    try {
      var postgres = EmbeddedPostgres.builder().setPort(0).start();
      try {
        org.flywaydb.core.Flyway.configure()
            .dataSource(postgres.getPostgresDatabase())
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return postgres;
      } catch (Exception failure) {
        postgres.close();
        throw failure;
      }
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url",
        () -> {
          try (var connection = POSTGRES.getPostgresDatabase().getConnection()) {
            return connection.getMetaData().getURL();
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "postgres");
  }

  @AfterAll
  static void stop() throws Exception {
    POSTGRES.close();
  }

  @Autowired MockMvc http;
  @Autowired ApplicationContext application;
  @Autowired JdbcTemplate db;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void onlyFourAuthenticationPostEndpointsRemain() {
    var authMappings =
        mappings.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().equals(AuthController.class))
            .toList();
    Set<String> paths =
        authMappings.stream()
            .flatMap(entry -> entry.getKey().getPatternValues().stream())
            .collect(Collectors.toSet());
    assertThat(paths)
        .containsExactlyInAnyOrder(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");
    authMappings.forEach(
        entry ->
            assertThat(entry.getKey().getMethodsCondition().getMethods())
                .containsExactly(RequestMethod.POST));
    var allPaths =
        mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .collect(Collectors.toSet());
    assertThat(allPaths)
        .doesNotContain("/api/v1/auth/me", "/api/v1/auth/switch-tenant", "/internal/authorization");
  }

  @Test
  void stubsReturn501WithoutIssuingCookiesOrWritingData() throws Exception {
    Long users = db.queryForObject("SELECT count(*) FROM users", Long.class);
    Long events = db.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
    for (String action : List.of("register", "login", "refresh", "logout")) {
      http.perform(
              post("/api/v1/auth/" + action)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"reset@example.com\",\"password\":\"StrongPassword123!\"}"))
          .andExpect(status().isNotImplemented())
          .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
          .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
          .andExpect(jsonPath("$.code").value("auth_not_implemented"));
    }
    assertThat(db.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(users);
    assertThat(db.queryForObject("SELECT count(*) FROM outbox_events", Long.class))
        .isEqualTo(events);
  }

  @Test
  void oldAuthenticationInfrastructureIsNotAutoCreated() {
    assertThat(application.getBeansOfType(JwtTokenProvider.class)).isEmpty();
    assertThat(
            application.getBeansOfType(
                io.github.coreplatform.common.security.jwt.JwtTokenService.class))
        .isEmpty();
    assertThat(application.getBeansOfType(GatewayContextCodec.class)).isEmpty();
    assertThat(application.containsBean("springSecurityFilterChain")).isFalse();
    assertThat(application.containsBean("inMemoryUserDetailsManager")).isFalse();
    assertThat(application.containsBean("stringRedisTemplate")).isFalse();
  }

  @Test
  void businessEndpointsStayBlockedEvenWithSpoofedIdentityHeaders() throws Exception {
    for (String path : List.of("/api/v1/tenants", "/api/v1/cms/users", "/api/v1/cms/tenants")) {
      http.perform(
              get(path)
                  .header("X-User-Id", java.util.UUID.randomUUID().toString())
                  .header("X-Gateway-Context", "forged")
                  .header("X-System-Role", "PLATFORM_ADMIN"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("iam_authentication_reset"));
    }
    http.perform(
            put("/api/v1/cms/tenants/" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void healthAndOpenApiRemainAccessibleWithoutTheOldSecurityStack() throws Exception {
    http.perform(get("/actuator/health")).andExpect(status().isOk());
    var result = http.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var document =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString());
    assertThat(document.path("paths").size()).isEqualTo(4);
    for (String action : List.of("login", "register", "refresh", "logout"))
      assertThat(document.path("paths").has("/api/v1/auth/" + action)).isTrue();
    assertThat(document.path("components").has("securitySchemes")).isFalse();
  }

  @Test
  void requestContractsStillRejectInvalidLoginAndRegistration() throws Exception {
    for (String action : List.of("login", "register")) {
      http.perform(
              post("/api/v1/auth/" + action)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"invalid\",\"password\":\"\"}"))
          .andExpect(status().isUnprocessableEntity());
    }
  }
}
