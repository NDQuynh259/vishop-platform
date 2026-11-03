package com.vishop.iam;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.infra.authorization.AuthorizationProjection;
import com.vishop.iam.modules.identity.application.api.IdentityApi;
import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
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
      "vishop.cache.key-prefix=iam-test:cache:",
      "vishop.outbox.enabled=false",
      "vishop.security.authorization.initial-delay-ms=600000",
      "vishop.security.authorization.eager-publish=false",
      "management.tracing.enabled=false",
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
  @Autowired com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort sessions;
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
    return postAuth("logout").cookie(new Cookie(AuthCookies.REFRESH_TOKEN, c.refresh()));
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
  void sessionCreationTimeIsGeneratedByDatabaseAndPreservedThroughRefreshAndLogout()
      throws Exception {
    var credentials = register("session-timestamp@example.com");
    UUID sessionId = sid(credentials);
    var session = sessions.find(sessionId).orElseThrow();
    Instant createdAt =
        db.queryForObject(
                "SELECT created_at FROM auth_sessions WHERE id=?",
                java.sql.Timestamp.class,
                sessionId)
            .toInstant();
    assertThat(session.createdAt()).isNotNull().isEqualTo(createdAt);
    assertThat(session.createdAt()).isBefore(session.expiresAt());
    assertThat(session.userId()).isEqualTo(credentials.userId());
    assertThat(session.revokedAt()).isNull();
    http.perform(refresh(credentials)).andExpect(status().isOk());
    var refreshed = sessions.find(sessionId).orElseThrow();
    assertThat(refreshed.createdAt()).isEqualTo(createdAt);
    assertThat(refreshed.expiresAt()).isEqualTo(session.expiresAt());
    assertThat(refreshed.tokenVersion()).isEqualTo(session.tokenVersion());
    http.perform(logout(credentials)).andExpect(status().isNoContent());
    var revoked = sessions.find(sessionId).orElseThrow();
    assertThat(revoked.createdAt()).isEqualTo(createdAt);
    assertThat(revoked.expiresAt()).isEqualTo(session.expiresAt());
    assertThat(revoked.revokedAt()).isNotNull().isAfterOrEqualTo(createdAt);
    assertThat(revoked.active(Instant.now())).isFalse();
    http.perform(logout(credentials)).andExpect(status().isNoContent());
    assertThat(sessions.find(sessionId).orElseThrow().revokedAt()).isEqualTo(revoked.revokedAt());
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
    String key = "iam-test:cache:" + AuthorizationCacheKeys.session(sid(a));
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
  void logoutUsesVerifiedGatewayContextWithoutAnyRawToken() throws Exception {
    var c = register("context-logout@example.com");
    http.perform(protectedRequest(c, "POST", "/api/v1/auth/logout"))
        .andExpect(status().isNoContent());
    assertThat(sessions.find(sid(c)).orElseThrow().revokedAt()).isNotNull();
    http.perform(refresh(c)).andExpect(status().isUnauthorized());
  }

  @Test
  void rawAccessCookieAloneDoesNotAuthenticateLogoutAtIam() throws Exception {
    var c = register("raw-access-logout@example.com");
    http.perform(postAuth("logout").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, c.access())))
        .andExpect(status().isNoContent());
    assertThat(sessions.find(sid(c)).orElseThrow().revokedAt()).isNull();
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='UserLoggedOut'",
                Integer.class))
        .isZero();
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
            new com.vishop.iam.shared.application.port.AuthorizationDeliveryPort.Unavailable(
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
    http.perform(refresh(selected)).andExpect(status().isForbidden());
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
  void deactivatingMembershipPreservesTheOtherDevicesTenantAndSessions() throws Exception {
    var managerIdentity = register("membership-manager@example.com");
    UUID first = tenant(managerIdentity, "member-first");
    var manager = select(managerIdentity, first);
    var user = register("two-devices@example.com");
    UUID second = tenant(user, "member-second");
    UUID firstRole =
        db.queryForObject(
            "SELECT id FROM roles WHERE tenant_id=? AND builtin_key='WAREHOUSE'",
            UUID.class,
            first);
    db.update(
        "INSERT INTO tenant_members(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        first,
        user.userId(),
        firstRole);
    var firstDevice = select(user, first);
    var secondDevice = select(login("two-devices@example.com"), second);
    assertThat(sid(firstDevice)).isNotEqualTo(sid(secondDevice));
    String path = "/api/v1/tenants/" + first + "/members/" + user.userId();

    http.perform(protectedRequest(manager, "DELETE", path)).andExpect(status().isNoContent());

    assertThat(
            db.queryForObject(
                "SELECT token_version FROM users WHERE id=?", Long.class, user.userId()))
        .isEqualTo(1L);
    assertThatThrownBy(() -> identity.verifyAccessToken(firstDevice.access()))
        .isInstanceOf(io.github.coreplatform.common.domain.exception.ForbiddenException.class);
    assertThat(identity.verifyAccessToken(secondDevice.access()).tenantId()).isEqualTo(second);
    http.perform(refresh(firstDevice)).andExpect(status().isForbidden());
    http.perform(refresh(secondDevice)).andExpect(status().isOk());
    // A live identity session can switch away from the inactive tenant without logging in again.
    select(firstDevice, second);
    String firstKey = "iam-test:cache:" + AuthorizationCacheKeys.member(first, user.userId());
    String secondKey = "iam-test:cache:" + AuthorizationCacheKeys.member(second, user.userId());
    assertThat(redis.<String, String>opsForHash().get(firstKey, "payload")).contains("INACTIVE");
    assertThat(redis.<String, String>opsForHash().get(secondKey, "payload")).contains("ACTIVE");
    assertThat(
            redis.hasKey("iam-test:cache:" + AuthorizationCacheKeys.member(first, user.userId())))
        .isTrue();
  }

  @Test
  void membershipCacheDeliveryFailureCanBeRetriedWithoutDuplicateAudit() throws Exception {
    var managerIdentity = register("retry-member-manager@example.com");
    UUID tenant = tenant(managerIdentity, "retry-member");
    var manager = select(managerIdentity, tenant);
    var member = register("retry-member@example.com");
    UUID role =
        db.queryForObject(
            "SELECT id FROM roles WHERE tenant_id=? AND builtin_key='WAREHOUSE'",
            UUID.class,
            tenant);
    db.update(
        "INSERT INTO tenant_members(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        member.userId(),
        role);
    org.mockito.Mockito.doThrow(
            new com.vishop.iam.shared.application.port.AuthorizationDeliveryPort.Unavailable(
                new RuntimeException("Redis down")))
        .doCallRealMethod()
        .when(projection)
        .deliverMembership(tenant, member.userId());
    String path = "/api/v1/tenants/" + tenant + "/members/" + member.userId();

    http.perform(protectedRequest(manager, "DELETE", path))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT status FROM tenant_members WHERE tenant_id=? AND user_id=?",
                String.class,
                tenant,
                member.userId()))
        .isEqualTo("INACTIVE");
    assertThat(
            db.queryForObject(
                "SELECT pending FROM authorization_outbox WHERE state_key=?",
                Boolean.class,
                AuthorizationCacheKeys.member(tenant, member.userId())))
        .isTrue();
    http.perform(protectedRequest(manager, "DELETE", path)).andExpect(status().isNoContent());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='MemberDeactivated'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT pending FROM authorization_outbox WHERE state_key=?",
                Boolean.class,
                AuthorizationCacheKeys.member(tenant, member.userId())))
        .isFalse();
  }

  @Test
  void cacheLoaderRequiresTechnicalGatewayContext() throws Exception {
    var c = register("cache@example.com");
    URI target =
        URI.create(
            "/internal/authorization?key="
                + URLEncoder.encode(
                    AuthorizationCacheKeys.session(sid(c)),
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

  private UUID createRole(Credentials owner, UUID tenant, String name, Set<String> permissions)
      throws Exception {
    var response =
        http.perform(
                payload(
                    protectedRequest(owner, "POST", "/api/v1/tenants/" + tenant + "/roles"),
                    Map.of("name", name, "permissions", permissions)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        json.readTree(response.getResponse().getContentAsString()).path("id").asText());
  }

  @Test
  void customRoleEditsAndAssignmentsUseCurrentPermissionsWithoutRevokingSessions()
      throws Exception {
    var ownerIdentity = register("role-owner@example.com");
    UUID tenant = tenant(ownerIdentity, "custom-role-flow");
    var owner = select(ownerIdentity, tenant);
    UUID role = createRole(owner, tenant, "Sales", Set.of("tenant.view"));
    var memberIdentity = register("sales@example.com");
    db.update(
        "INSERT INTO tenant_members(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        memberIdentity.userId(),
        role);
    var member = select(memberIdentity, tenant);
    http.perform(protectedRequest(member, "GET", "/api/v1/tenants/" + tenant))
        .andExpect(status().isOk());
    String rolesPath = "/api/v1/tenants/" + tenant + "/roles";
    http.perform(
            payload(
                protectedRequest(owner, "POST", rolesPath),
                Map.of("name", "Invalid", "permissions", Set.of("unknown.permission"))))
        .andExpect(status().isBadRequest());
    http.perform(
            payload(
                protectedRequest(owner, "PUT", rolesPath + "/" + role),
                Map.of("name", "Sales", "permissions", Set.of("tenant.member.view"))))
        .andExpect(status().isOk());
    // Permission edits affect current requests without revoking or refreshing this token.
    assertThat(
            db.queryForObject(
                "SELECT token_version FROM users WHERE id=?", Long.class, member.userId()))
        .isEqualTo(1L);
    http.perform(protectedRequest(member, "GET", "/api/v1/tenants/" + tenant))
        .andExpect(status().isForbidden());
    http.perform(protectedRequest(member, "GET", rolesPath)).andExpect(status().isOk());
    projection.publish();
    var cached = projection.find(AuthorizationCacheKeys.role(tenant, role));
    assertThat(cached.permissions()).containsExactly("tenant.member.view");
    assertThat(redis.<String, String>opsForHash().get("iam-test:cache:" + cached.key(), "payload"))
        .contains("tenant.member.view")
        .doesNotContain("tenant.view");
    UUID nextRole = createRole(owner, tenant, "Viewer", Set.of("tenant.view"));
    http.perform(
            payload(
                protectedRequest(
                    owner,
                    "PUT",
                    "/api/v1/tenants/" + tenant + "/members/" + member.userId() + "/role"),
                Map.of("roleId", nextRole)))
        .andExpect(status().isNoContent());
    var updated = credentials(http.perform(refresh(member)).andExpect(status().isOk()).andReturn());
    http.perform(protectedRequest(updated, "GET", rolesPath)).andExpect(status().isForbidden());
    assertThat(identity.verifyAccessToken(member.access()).permissions())
        .containsExactly("tenant.view");
    var loggedIn = login("sales@example.com");
    assertThat(jwt.verifyAccessToken(loggedIn.access()).get("tenantRoleId", String.class))
        .isEqualTo(nextRole.toString());
    assertThat(loggedIn.body().path("permissions").toString())
        .contains("tenant.view")
        .doesNotContain("tenant.member.view");
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "membership"})
  void softDeletedOwnersDoNotAllowRemovingTheLastUsableOwner(String deleted) throws Exception {
    var ownerIdentity = register("last-owner@example.com");
    UUID tenant = tenant(ownerIdentity, "last-owner-check");
    var owner = select(ownerIdentity, tenant);
    UUID ownerRole =
        UUID.fromString(jwt.verifyAccessToken(owner.access()).get("tenantRoleId", String.class));
    var removed = register("removed-owner@example.com");
    db.update(
        "INSERT INTO tenant_members(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        removed.userId(),
        ownerRole);
    if ("user".equals(deleted))
      db.update("UPDATE users SET deleted_at=clock_timestamp() WHERE id=?", removed.userId());
    else
      db.update(
          "UPDATE tenant_members SET deleted_at=clock_timestamp() WHERE tenant_id=? AND user_id=?",
          tenant,
          removed.userId());
    http.perform(
            protectedRequest(
                owner, "DELETE", "/api/v1/tenants/" + tenant + "/members/" + owner.userId()))
        .andExpect(status().isConflict());
    assertThat(
            db.queryForObject(
                "SELECT status FROM tenant_members WHERE tenant_id=? AND user_id=?",
                String.class,
                tenant,
                owner.userId()))
        .isEqualTo("ACTIVE");
  }

  @Test
  void customRoleManagerCanInviteWithinTheirGrantsButCannotEscalatePermissions() throws Exception {
    var ownerIdentity = register("scoped-owner@example.com");
    UUID tenant = tenant(ownerIdentity, "scoped-manager");
    var owner = select(ownerIdentity, tenant);
    UUID managerRole =
        createRole(
            owner,
            tenant,
            "Member manager",
            Set.of(
                "tenant.view",
                "tenant.member.view",
                "tenant.member.invite",
                "tenant.member.role_assign"));
    UUID viewerRole = createRole(owner, tenant, "Viewer", Set.of("tenant.view"));
    var managerIdentity = register("manager@example.com");
    db.update(
        "INSERT INTO tenant_members(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        managerIdentity.userId(),
        managerRole);
    var manager = select(managerIdentity, tenant);
    var recipient = register("viewer@example.com");
    String rolesPath = "/api/v1/tenants/" + tenant + "/roles";
    http.perform(
            payload(
                protectedRequest(manager, "POST", rolesPath),
                Map.of("name", "Escalated", "permissions", Set.of("billing.manage"))))
        .andExpect(status().isForbidden());
    UUID ownerRole =
        UUID.fromString(jwt.verifyAccessToken(owner.access()).get("tenantRoleId", String.class));
    String invitePath = "/api/v1/tenants/" + tenant + "/invitations";
    http.perform(
            payload(
                protectedRequest(manager, "POST", invitePath),
                Map.of("email", "viewer@example.com", "roleId", ownerRole)))
        .andExpect(status().isForbidden());
    var response =
        http.perform(
                payload(
                    protectedRequest(manager, "POST", invitePath),
                    Map.of("email", "viewer@example.com", "roleId", viewerRole)))
            .andExpect(status().isCreated())
            .andReturn();
    var invitation = json.readTree(response.getResponse().getContentAsString());
    http.perform(
            payload(
                protectedRequest(recipient, "POST", "/api/v1/invitations/accept"),
                Map.of("token", invitation.path("token").asText())))
        .andExpect(status().isOk());
    var joined = select(recipient, tenant);
    assertThat(jwt.verifyAccessToken(joined.access()).get("tenantRoleId", String.class))
        .isEqualTo(viewerRole.toString());
    http.perform(protectedRequest(joined, "GET", "/api/v1/tenants/" + tenant))
        .andExpect(status().isOk());
    http.perform(protectedRequest(joined, "GET", rolesPath)).andExpect(status().isForbidden());
    // The same invitation is single-use; it must not add another member or acceptance event.
    http.perform(
            payload(
                protectedRequest(recipient, "POST", "/api/v1/invitations/accept"),
                Map.of("token", invitation.path("token").asText())))
        .andExpect(status().isBadRequest());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='InvitationAccepted'",
                Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"system-role", "status"})
  void softDeletedAdminsDoNotAllowDisablingTheLastUsableAdmin(String change) throws Exception {
    var adminIdentity = register("last-admin@example.com");
    var removed = register("removed-admin@example.com");
    db.update(
        "UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id IN (?,?)",
        adminIdentity.userId(),
        removed.userId());
    db.update("UPDATE users SET deleted_at=clock_timestamp() WHERE id=?", removed.userId());
    var admin = login("last-admin@example.com");
    var body =
        "status".equals(change) ? Map.of("status", "SUSPENDED") : Map.of("systemRole", "USER");
    http.perform(
            payload(
                protectedRequest(
                    admin, "PATCH", "/api/v1/cms/users/" + admin.userId() + "/" + change),
                body))
        .andExpect(status().isConflict());
    assertThat(
            db.queryForObject(
                "SELECT system_role FROM users WHERE id=?", String.class, admin.userId()))
        .isEqualTo("PLATFORM_ADMIN");
    assertThat(
            db.queryForObject("SELECT status FROM users WHERE id=?", String.class, admin.userId()))
        .isEqualTo("ACTIVE");
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspendedUser", "deletedUser", "deletedMembership", "deletedRole"})
  void invitationCannotBeAcceptedAfterIssuerLosesActiveAccess(String revoked) throws Exception {
    var inviterIdentity = register("inviter@example.com");
    UUID tenant = tenant(inviterIdentity, "invitation-check");
    var inviter = select(inviterIdentity, tenant);
    var recipient = register("recipient@example.com");
    UUID targetRole =
        db.queryForObject(
            "SELECT id FROM roles WHERE tenant_id=? AND builtin_key='WAREHOUSE'",
            UUID.class,
            tenant);
    var result =
        http.perform(
                payload(
                    protectedRequest(inviter, "POST", "/api/v1/tenants/" + tenant + "/invitations"),
                    Map.of("email", "recipient@example.com", "roleId", targetRole)))
            .andExpect(status().isCreated())
            .andReturn();
    var invitation = json.readTree(result.getResponse().getContentAsString());
    switch (revoked) {
      case "suspendedUser" ->
          db.update("UPDATE users SET status='SUSPENDED' WHERE id=?", inviter.userId());
      case "deletedUser" ->
          db.update("UPDATE users SET deleted_at=clock_timestamp() WHERE id=?", inviter.userId());
      case "deletedMembership" ->
          db.update(
              "UPDATE tenant_members SET deleted_at=clock_timestamp() WHERE tenant_id=? AND user_id=?",
              tenant,
              inviter.userId());
      case "deletedRole" ->
          db.update(
              "UPDATE roles SET deleted_at=clock_timestamp() WHERE tenant_id=? AND builtin_key='OWNER'",
              tenant);
      default -> throw new IllegalArgumentException(revoked);
    }
    http.perform(
            payload(
                protectedRequest(recipient, "POST", "/api/v1/invitations/accept"),
                Map.of("token", invitation.path("token").asText())))
        .andExpect(status().isForbidden());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM tenant_members WHERE tenant_id=? AND user_id=?",
                Integer.class,
                tenant,
                recipient.userId()))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT status FROM tenant_invitations WHERE id=?",
                String.class,
                UUID.fromString(invitation.path("id").asText())))
        .isEqualTo("PENDING");
  }
}
