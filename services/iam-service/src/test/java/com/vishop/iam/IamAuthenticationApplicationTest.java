package com.vishop.iam;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.infra.authorization.AuthorizationProjection;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.github.coreplatform.common.security.gateway.*;
import io.github.coreplatform.common.security.jwt.JwtTokenService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import redis.embedded.RedisServer;

@SpringBootTest(
    properties = {
      "vishop.outbox.enabled=false", "vishop.security.authorization.initial-delay-ms=600000",
      "vishop.security.authorization.eager-publish=false", "management.tracing.enabled=false",
      "vishop.security.cookies.secure=true"
    })
@AutoConfigureMockMvc
class IamAuthenticationApplicationTest {
  private static final EmbeddedPostgres PG = startPostgres();
  private static final int REDIS_PORT = port();
  private static final RedisServer REDIS = startRedis();

  private static EmbeddedPostgres startPostgres() {
    try {
      var pg = EmbeddedPostgres.builder().setPort(0).start();
      org.flywaydb.core.Flyway.configure()
          .dataSource(pg.getPostgresDatabase())
          .locations("classpath:db/migration")
          .load()
          .migrate();
      return pg;
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static int port() {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static RedisServer startRedis() {
    try {
      var redis =
          RedisServer.newRedisServer()
              .port(REDIS_PORT)
              .setting("bind 127.0.0.1")
              .setting("maxmemory 64M")
              .build();
      redis.start();
      return redis;
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry props) {
    props.add(
        "spring.datasource.url",
        () -> {
          try (var c = PG.getPostgresDatabase().getConnection()) {
            return c.getMetaData().getURL();
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    props.add("spring.datasource.username", () -> "postgres");
    props.add("spring.datasource.password", () -> "postgres");
    props.add("spring.data.redis.host", () -> "127.0.0.1");
    props.add("spring.data.redis.port", () -> REDIS_PORT);
  }

  @AfterAll
  static void close() throws Exception {
    REDIS.stop();
    PG.close();
  }

  @Autowired MockMvc http;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenService jwt;
  @Autowired GatewayContextCodec context;
  @Autowired IdentityApi identity;
  @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

  @SpyBean(proxyTargetAware = true)
  AuthorizationProjection projection;

  @BeforeEach
  void reset() {
    db.execute("TRUNCATE users,tenants,outbox_events,authorization_outbox CASCADE");
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
  }

  record Credentials(UUID userId, String access, String refresh, JsonNode body) {}

  private MockHttpServletRequestBuilder postAuth(String action) {
    return post("/api/v1/auth/" + action)
        .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE);
  }

  private MockHttpServletRequestBuilder payload(MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
  }

  private Credentials credentials(MvcResult result) throws Exception {
    String access = result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getValue();
    String refresh = result.getResponse().getCookie(AuthCookies.REFRESH_TOKEN).getValue();
    return new Credentials(
        UUID.fromString(jwt.verifyAccessToken(access).getSubject()),
        access,
        refresh,
        json.readTree(result.getResponse().getContentAsString()));
  }

  private Credentials register(String email) throws Exception {
    return credentials(
        http.perform(
                payload(
                    postAuth("register"), Map.of("email", email, "password", "StrongPassword123!")))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private Credentials login(String email) throws Exception {
    return credentials(
        http.perform(
                payload(
                    postAuth("login"), Map.of("email", email, "password", "StrongPassword123!")))
            .andExpect(status().isOk())
            .andReturn());
  }

  private UUID sid(Credentials c) {
    return UUID.fromString(jwt.verifyAccessToken(c.access()).get("sid", String.class));
  }

  private MockHttpServletRequestBuilder refresh(Credentials c) {
    return postAuth("refresh").cookie(new Cookie(AuthCookies.REFRESH_TOKEN, c.refresh()));
  }

  private MockHttpServletRequestBuilder logout(Credentials c) {
    return postAuth("logout")
        .cookie(
            new Cookie(AuthCookies.ACCESS_TOKEN, c.access()),
            new Cookie(AuthCookies.REFRESH_TOKEN, c.refresh()));
  }

  private MockHttpServletRequestBuilder protectedRequest(
      Credentials c, String method, String path) {
    var claims = jwt.verifyAccessToken(c.access());
    String tenant = claims.get("tenantId", String.class),
        role = claims.get("tenantRoleId", String.class);
    var actor =
        new GatewayPrincipal(
            c.userId(),
            tenant == null ? null : UUID.fromString(tenant),
            claims.get("systemRole", String.class),
            claims.get("tenantRole", String.class),
            Set.of(),
            sid(c));
    var request =
        request(org.springframework.http.HttpMethod.valueOf(method), URI.create(path))
            .header(
                GatewayContextCodec.HEADER,
                context.sign(actor, role == null ? null : UUID.fromString(role), method, path));
    if (AuthCookies.requiresRequestHeader(method))
      request.header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE);
    return request;
  }

  private UUID tenant(Credentials c, String code) throws Exception {
    var result =
        http.perform(
                payload(
                    protectedRequest(c, "POST", "/api/v1/tenants"),
                    Map.of("code", code, "name", "Tenant " + code)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        json.readTree(result.getResponse().getContentAsString()).path("id").asText());
  }

  private Credentials select(Credentials c, UUID tenant) throws Exception {
    return credentials(
        http.perform(payload(refresh(c), Map.of("tenantId", tenant)))
            .andExpect(status().isOk())
            .andReturn());
  }

  @Test
  void registrationIssuesTwoKeysCookiesAndSafeJson() throws Exception {
    var result =
        http.perform(
                payload(
                    postAuth("register"),
                    Map.of("email", "New@Example.com", "password", "StrongPassword123!")))
            .andExpect(status().isCreated())
            .andReturn();
    var c = credentials(result);
    assertThat(
            c.body().has("accessToken")
                || c.body().has("refreshToken")
                || c.body().has("passwordHash"))
        .isFalse();
    assertThat(c.body().path("user").path("systemRole").asText()).isEqualTo("USER");
    String hash =
        db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, c.userId());
    assertThat(hash).startsWith("$2").isNotEqualTo("StrongPassword123!");
    var access = jwt.verifyAccessToken(c.access());
    var refresh = jwt.verifyRefreshToken(c.refresh());
    assertThat(access.get("sid")).isEqualTo(refresh.get("sid"));
    assertThat(access).doesNotContainKeys("permissions", "systemRoleId", "roles");
    assertThat(access.getAudience()).containsExactly("vishop-api");
    assertThat(refresh.getAudience()).containsExactly("vishop-refresh");
    assertThatThrownBy(() -> jwt.verifyAccessToken(c.refresh()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> jwt.verifyRefreshToken(c.access()))
        .isInstanceOf(RuntimeException.class);
    assertThat(result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).isHttpOnly()).isTrue();
    assertThat(result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getSecure()).isTrue();
    assertThat(result.getResponse().getCookie(AuthCookies.REFRESH_TOKEN).getPath())
        .isEqualTo("/api/v1/auth");
    assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    http.perform(
            payload(
                postAuth("register"),
                Map.of("email", "new@example.com", "password", "StrongPassword123!")))
        .andExpect(status().isConflict());
    assertThat(db.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
  }

  @Test
  void invalidLoginDoesNotCreateSessionOrAudit() throws Exception {
    register("login@example.com");
    for (String email : List.of("login@example.com", "unknown@example.com"))
      http.perform(
              payload(postAuth("login"), Map.of("email", email, "password", "WrongPassword123!")))
          .andExpect(status().isUnauthorized());
    assertThat(db.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    http.perform(
            payload(
                postAuth("register"),
                Map.of(
                    "email",
                    "inject@example.com",
                    "password",
                    "StrongPassword123!",
                    "systemRole",
                    "PLATFORM_ADMIN")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refreshKeepsSidAndAbsoluteDeadlineWithoutRequiringAccess() throws Exception {
    var c = register("refresh@example.com");
    var refreshed = credentials(http.perform(refresh(c)).andExpect(status().isOk()).andReturn());
    assertThat(sid(refreshed)).isEqualTo(sid(c));
    assertThat(refreshed.refresh()).isEqualTo(c.refresh());
    assertThat(jwt.verifyRefreshToken(refreshed.refresh()).getExpiration())
        .isEqualTo(jwt.verifyRefreshToken(c.refresh()).getExpiration());
    http.perform(postAuth("refresh").cookie(new Cookie(AuthCookies.REFRESH_TOKEN, c.access())))
        .andExpect(status().isUnauthorized());
    var forged =
        jwt.generateAccessToken(
            new HashMap<>(jwt.verifyRefreshToken(c.refresh())), Duration.ofMinutes(1));
    http.perform(postAuth("refresh").cookie(new Cookie(AuthCookies.REFRESH_TOKEN, forged)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesOnlyOneDeviceAndIsIdempotent() throws Exception {
    var a = register("devices@example.com");
    var b = login("devices@example.com");
    assertThat(sid(a)).isNotEqualTo(sid(b));
    projection.publish();
    String key = "vishop:" + AuthorizationSnapshot.session(sid(a));
    var result = http.perform(logout(a)).andExpect(status().isNoContent()).andReturn();
    assertThat(result.getResponse().getCookie(AuthCookies.ACCESS_TOKEN).getMaxAge()).isZero();
    assertThat(redis.<String, String>opsForHash().get(key, "payload")).contains("REVOKED");
    assertThat(redis.getExpire(key)).isPositive().isLessThanOrEqualTo(604800);
    String revision = redis.<String, String>opsForHash().get(key, "version");
    http.perform(logout(a)).andExpect(status().isNoContent());
    assertThat(redis.<String, String>opsForHash().get(key, "version")).isEqualTo(revision);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='UserLoggedOut'",
                Integer.class))
        .isEqualTo(1);
    http.perform(refresh(a)).andExpect(status().isUnauthorized());
    http.perform(protectedRequest(a, "GET", "/api/v1/tenants"))
        .andExpect(status().isUnauthorized());
    http.perform(refresh(b)).andExpect(status().isOk());
    assertThat(identity.verifyAccessToken(b.access()).userId()).isEqualTo(b.userId());
  }

  @Test
  void concurrentLogoutEmitsExactlyOneEvent() throws Exception {
    var c = register("concurrent-logout@example.com");
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var ready = new java.util.concurrent.CountDownLatch(2);
      var release = new java.util.concurrent.CountDownLatch(1);
      var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  release.await();
                  return http.perform(logout(c)).andReturn().getResponse().getStatus();
                }));
      assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      release.countDown();
      for (var future : futures)
        assertThat(future.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(204);
    }
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='UserLoggedOut'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void userContextCannotUseAnotherUsersSession() throws Exception {
    var a = register("bound-a@example.com");
    var b = register("bound-b@example.com");
    var forged = new GatewayPrincipal(a.userId(), null, "USER", null, Set.of(), sid(b));
    http.perform(
            get("/api/v1/tenants")
                .header(
                    GatewayContextCodec.HEADER,
                    context.sign(forged, null, "GET", "/api/v1/tenants")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void retryAfterCacheFailureDoesNotDuplicateLogoutAudit() throws Exception {
    var c = register("retry-logout@example.com");
    org.mockito.Mockito.doThrow(
            new IdentityApi.AuthorizationDeliveryPort.Unavailable(
                new RuntimeException("Redis down")))
        .doCallRealMethod()
        .when(projection)
        .deliverSession(sid(c));
    http.perform(logout(c)).andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE id=?",
                Boolean.class,
                sid(c)))
        .isTrue();
    http.perform(logout(c)).andExpect(status().isNoContent());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='UserLoggedOut'",
                Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"status", "password_hash", "system_role", "deleted_at"})
  void accountSecurityChangesRevokeAllDevices(String field) throws Exception {
    var a = register("revoke@example.com");
    var b = login("revoke@example.com");
    String value =
        switch (field) {
          case "status" -> "'SUSPENDED'";
          case "password_hash" -> "'changed-hash'";
          case "system_role" -> "'PLATFORM_ADMIN'";
          default -> "clock_timestamp()";
        };
    db.update("UPDATE users SET " + field + "=" + value + " WHERE id=?", a.userId());
    http.perform(refresh(a)).andExpect(status().isUnauthorized());
    http.perform(refresh(b)).andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> identity.verifyAccessToken(a.access()))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void tenantSelectionMembershipRevocationAndPermissionEditsUseCurrentState() throws Exception {
    var identityToken = register("tenant@example.com");
    UUID tenant = tenant(identityToken, "auth-tenant");
    var selected = select(identityToken, tenant);
    var oldDeadline = jwt.verifyRefreshToken(identityToken.refresh()).getExpiration();
    assertThat(jwt.verifyRefreshToken(selected.refresh()).getExpiration()).isEqualTo(oldDeadline);
    assertThat(selected.body().path("tenantRole").asText()).isEqualTo("OWNER");
    assertThat(selected.body().path("permissions").size()).isPositive();
    http.perform(protectedRequest(selected, "GET", "/api/v1/tenants/" + tenant))
        .andExpect(status().isOk());
    UUID role =
        UUID.fromString(jwt.verifyAccessToken(selected.access()).get("tenantRoleId", String.class));
    db.update("DELETE FROM role_permissions WHERE role_id=?", role);
    projection.publish();
    http.perform(protectedRequest(selected, "GET", "/api/v1/tenants/" + tenant))
        .andExpect(status().isForbidden());
    var refreshed =
        credentials(http.perform(refresh(selected)).andExpect(status().isOk()).andReturn());
    assertThat(refreshed.body().path("permissions").size()).isZero();
    assertThat(
            db.queryForObject(
                "SELECT token_version FROM users WHERE id=?", Long.class, selected.userId()))
        .isEqualTo(1L);
    db.update(
        "UPDATE tenant_members SET status='INACTIVE' WHERE user_id=? AND tenant_id=?",
        selected.userId(),
        tenant);
    http.perform(refresh(selected)).andExpect(status().isUnauthorized());
  }

  @Test
  void crossTenantSelectionIsDeniedAndDirectHeadersDoNotAuthenticate() throws Exception {
    var a = register("a@example.com");
    var b = register("b@example.com");
    UUID foreign = tenant(b, "foreign-tenant");
    http.perform(payload(refresh(a), Map.of("tenantId", foreign)))
        .andExpect(status().isForbidden());
    http.perform(
            get("/api/v1/tenants")
                .header("X-User-Id", a.userId().toString())
                .header("X-System-Role", "PLATFORM_ADMIN"))
        .andExpect(status().isUnauthorized());
    http.perform(get("/api/v1/tenants").header("Authorization", "Bearer " + a.access()))
        .andExpect(status().isUnauthorized());
    http.perform(protectedRequest(a, "GET", "/api/v1/cms/users")).andExpect(status().isForbidden());
  }

  @Test
  void cacheLoaderRequiresTechnicalGatewayContext() throws Exception {
    var c = register("cache@example.com");
    URI target =
        URI.create(
            "/internal/authorization?key="
                + URLEncoder.encode(
                    AuthorizationSnapshot.session(sid(c)),
                    java.nio.charset.StandardCharsets.UTF_8));
    String bound = target.getRawPath() + "?" + target.getRawQuery();
    http.perform(get(target)).andExpect(status().isUnauthorized());
    var technical = new GatewayPrincipal(new UUID(0, 0), null, "USER", null, Set.of());
    http.perform(
            get(target)
                .header(GatewayContextCodec.HEADER, context.sign(technical, null, "GET", bound)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(c.userId().toString()));
  }

  @Test
  void writesRequireCsrfHeaderAndAllowedOrigin() throws Exception {
    http.perform(
            payload(
                post("/api/v1/auth/login"),
                Map.of("email", "x@example.com", "password", "StrongPassword123!")))
        .andExpect(status().isForbidden());
    http.perform(
            payload(
                postAuth("register").header("Origin", "https://untrusted.example"),
                Map.of("email", "x@example.com", "password", "StrongPassword123!")))
        .andExpect(status().isForbidden());
    http.perform(postAuth("logout")).andExpect(status().isNoContent());
    assertThat(db.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
  }
}
