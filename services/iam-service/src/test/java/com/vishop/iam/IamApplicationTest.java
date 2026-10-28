package com.vishop.iam;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.composition.onboarding.application.command.CreateTenantCommand;
import com.vishop.iam.composition.onboarding.application.command.CreateTenantCommandHandler;
import com.vishop.iam.database.JpaDatabaseFixture;
import com.vishop.iam.modules.identity.application.query.AuthenticateQuery;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.security.constants.AuthCookies;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "vishop.outbox.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "management.tracing.enabled=false",
      "vishop.security.cookies.secure=true",
      "vishop.security.jwt.secret=test-jwt-secret-with-at-least-thirty-two-bytes"
    })
@AutoConfigureMockMvc
class IamApplicationTest {
  private static final EmbeddedPostgres POSTGRES = start();

  private static EmbeddedPostgres start() {
    try {
      var postgres = EmbeddedPostgres.builder().setPort(0).start();
      try {
        org.flywaydb.core.Flyway.configure()
            .dataSource(postgres.getPostgresDatabase())
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .load()
            .migrate();
        return postgres;
      } catch (Exception failure) {
        postgres.close();
        throw failure;
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry props) {
    props.add(
        "spring.datasource.url",
        () -> {
          try (var c = POSTGRES.getPostgresDatabase().getConnection()) {
            return c.getMetaData().getURL();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
    props.add("spring.datasource.username", () -> "postgres");
    props.add("spring.datasource.password", () -> "postgres");
  }

  @AfterAll
  static void stop() throws Exception {
    POSTGRES.close();
  }

  @Autowired MockMvc http;
  @Autowired QueryBus queries;
  @Autowired ObjectMapper json;
  @Autowired EntityManager entityManager;
  JpaDatabaseFixture db;
  @Autowired io.github.coreplatform.common.security.jwt.JwtTokenProvider tokens;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired org.springframework.context.ApplicationContext context;

  @org.springframework.boot.test.mock.mockito.SpyBean(proxyTargetAware = true)
  IamEventPublisherPort eventsSpy;

  @BeforeEach
  void reset() {
    db = new JpaDatabaseFixture(entityManager, transactions);
    db.execute("TRUNCATE users,tenants,outbox_events,consumed_messages CASCADE");
  }

  record Account(String email, String id, String token, String refreshToken) {}

  record CookieAuth(JsonNode json, String accessToken, String refreshToken) {
    JsonNode path(String name) {
      return json.path(name);
    }
  }

  private CookieAuth cookieAuth(MvcResult result) throws Exception {
    JsonNode response = body(result);
    assertThat(response.has("accessToken")).isFalse();
    assertThat(response.has("refreshToken")).isFalse();
    assertThat(response.has("roles")).isFalse();
    assertThat(response.has("tenantRole")).isTrue();
    assertThat(response.path("tenantRole").isNull() || response.path("tenantRole").isTextual())
        .isTrue();
    var cookies =
        result.getResponse().getHeaders("Set-Cookie").stream()
            .map(org.springframework.mock.web.MockCookie::parse)
            .toList();
    assertThat(cookies)
        .hasSize(2)
        .allSatisfy(
            cookie -> {
              assertThat(cookie.isHttpOnly()).isTrue();
              assertThat(cookie.getSecure()).isTrue();
              assertThat(cookie.getSameSite()).isEqualTo("Lax");
              assertThat(cookie.getMaxAge()).isPositive();
              assertThat(cookie.getDomain()).isNull();
            });
    var access =
        cookies.stream()
            .filter(c -> c.getName().equals(AuthCookies.ACCESS_TOKEN))
            .findFirst()
            .orElseThrow();
    var refresh =
        cookies.stream()
            .filter(c -> c.getName().equals(AuthCookies.REFRESH_TOKEN))
            .findFirst()
            .orElseThrow();
    var claims = tokens.parseClaims(access.getValue());
    assertThat(claims).doesNotContainKey("roles");
    assertThat(claims.get("tenantRole", String.class))
        .isEqualTo(
            response.path("tenantRole").isNull() ? null : response.path("tenantRole").asText());
    assertThat(access.getPath()).isEqualTo(AuthCookies.ACCESS_PATH);
    assertThat(refresh.getPath()).isEqualTo(AuthCookies.REFRESH_PATH);
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    return new CookieAuth(response, access.getValue(), refresh.getValue());
  }

  private MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
    return post("/api/v1/auth/refresh")
        .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, refreshToken));
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder request, String token) {
    return request
        .header("Authorization", "Bearer " + token)
        .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE);
  }

  private MockHttpServletRequestBuilder payload(MockHttpServletRequestBuilder request, Object data)
      throws Exception {
    return request
        .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(data));
  }

  private Account register(String email) throws Exception {
    var result =
        http.perform(
                payload(
                    post("/api/v1/auth/register"),
                    Map.of("email", email, "password", "StrongPass123!")))
            .andExpect(status().isCreated())
            .andReturn();
    var response = cookieAuth(result);
    return new Account(
        email,
        response.path("user").path("id").asText(),
        response.accessToken(),
        response.refreshToken());
  }

  private JsonNode createTenant(Account account, String code) throws Exception {
    return body(
        http.perform(
                payload(
                    authorized(post("/api/v1/tenants"), account.token()),
                    Map.of("code", code, "name", code)))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private String role(String tenant, String key) {
    return db.queryForObject(
        "SELECT id::text FROM roles WHERE tenant_id=CAST(? AS uuid) AND builtin_key=?",
        String.class,
        tenant,
        key);
  }

  private String invite(Account owner, String tenant, String email, String role) throws Exception {
    return body(http.perform(
                payload(
                    authorized(post("/api/v1/tenants/" + tenant + "/invitations"), owner.token()),
                    Map.of("email", email, "roleId", role)))
            .andExpect(status().isCreated())
            .andReturn())
        .path("token")
        .asText();
  }

  private void accept(Account account, String token) throws Exception {
    http.perform(
            payload(
                authorized(post("/api/v1/invitations/accept"), account.token()),
                Map.of("token", token)))
        .andExpect(status().isOk());
  }

  private CookieAuth select(Account account, String tenant) throws Exception {
    return cookieAuth(
        http.perform(
                payload(
                    post("/api/v1/auth/switch-tenant")
                        .cookie(
                            new Cookie(AuthCookies.ACCESS_TOKEN, account.token()),
                            new Cookie(AuthCookies.REFRESH_TOKEN, account.refreshToken())),
                    Map.of("tenantId", tenant)))
            .andExpect(status().isOk())
            .andReturn());
  }

  @Test
  void listsTenantsFromAuthenticatedUserWithoutLeakingOtherTenants() throws Exception {
    Account owner = register("owner@example.com");
    Account other = register("other@example.com");
    String second = createTenant(owner, "beta-shop").path("id").asText();
    String first = createTenant(owner, "alpha-shop").path("id").asText();
    String suspended = createTenant(owner, "suspended-shop").path("id").asText();
    String inactive = createTenant(owner, "inactive-shop").path("id").asText();
    createTenant(other, "unrelated-shop");
    db.update("UPDATE tenants SET status='SUSPENDED' WHERE id=CAST(? AS uuid)", suspended);
    db.update(
        "UPDATE tenant_members SET status='INACTIVE' WHERE user_id=CAST(? AS uuid) AND tenant_id=CAST(? AS uuid)",
        owner.id(),
        inactive);

    http.perform(authorized(get("/api/v1/tenants").param("sort", "name:asc"), owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.object").value("list"))
        .andExpect(jsonPath("$.url").value("/api/v1/tenants"))
        .andExpect(jsonPath("$.pageInfo.page").value(1))
        .andExpect(jsonPath("$.pageInfo.limit").value(20))
        .andExpect(jsonPath("$.pageInfo.total").value(2))
        .andExpect(jsonPath("$.pageInfo.totalPages").value(1))
        .andExpect(jsonPath("$.pageInfo.hasNextPage").value(false))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(first))
        .andExpect(jsonPath("$.data[0].code").value("alpha-shop"))
        .andExpect(jsonPath("$.data[0].name").value("alpha-shop"))
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data[1].id").value(second));
    for (int page = 1; page <= 3; page++) {
      var response =
          http.perform(
                  authorized(
                      get("/api/v1/tenants")
                          .param("sort", "name:asc")
                          .param("page", String.valueOf(page))
                          .param("limit", "1"),
                      owner.token()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.pageInfo.page").value(page))
              .andExpect(jsonPath("$.pageInfo.limit").value(1))
              .andExpect(jsonPath("$.pageInfo.total").value(2))
              .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
              .andExpect(jsonPath("$.pageInfo.hasNextPage").value(page == 1));
      if (page <= 2) {
        response
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(page == 1 ? first : second));
      } else {
        response.andExpect(jsonPath("$.data.length()").value(0));
      }
    }
    http.perform(authorized(get("/api/v1/tenants"), owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(second))
        .andExpect(jsonPath("$.data[1].id").value(first));
    http.perform(
            authorized(
                get("/api/v1/tenants").param("page", "2147483647").param("limit", "100"),
                owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.pageInfo.total").value(2));
  }

  @Test
  void listingTenantsUsesPrincipalAndRequiresAnActiveUser() throws Exception {
    Account user = register("user@example.com");
    Account other = register("other@example.com");
    createTenant(other, "other-shop");
    http.perform(get("/api/v1/tenants")).andExpect(status().isUnauthorized());
    // Client-supplied IDs cannot override the authenticated principal.
    http.perform(
            authorized(
                get("/api/v1/tenants").param("userId", other.id()).header("X-User-Id", other.id()),
                user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.pageInfo.total").value(0));
    // System admins also receive only their own memberships on this endpoint.
    db.update("UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id=CAST(? AS uuid)", user.id());
    http.perform(authorized(get("/api/v1/tenants"), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
    db.update("UPDATE users SET status='SUSPENDED' WHERE id=CAST(? AS uuid)", user.id());
    http.perform(authorized(get("/api/v1/tenants"), user.token()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tenantListUsesSharedPaginationNormalizationAndRejectsUnsupportedCursor() throws Exception {
    Account user = register("pagination@example.com");
    http.perform(
            authorized(
                get("/api/v1/tenants").param("page", "0").param("limit", "-1"), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageInfo.page").value(1))
        .andExpect(jsonPath("$.pageInfo.limit").value(20))
        .andExpect(jsonPath("$.pageInfo.totalPages").value(0))
        .andExpect(jsonPath("$.pageInfo.hasNextPage").value(false));
    http.perform(authorized(get("/api/v1/tenants").param("limit", "1000"), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageInfo.limit").value(100));
    http.perform(authorized(get("/api/v1/tenants").param("cursor", "unsupported"), user.token()))
        .andExpect(status().isBadRequest());
    http.perform(authorized(get("/api/v1/tenants").param("page", "invalid"), user.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tenantListCombinesFieldFiltersSortingAndPaginationWithinCurrentUserScope() throws Exception {
    Account user = register("filter-user@example.com");
    Account other = register("filter-other@example.com");
    String first = createTenant(user, "shop-alpha").path("id").asText();
    String second = createTenant(user, "shop-beta").path("id").asText();
    String foreign = createTenant(other, "shop-other").path("id").asText();
    String inactive = createTenant(user, "shop-inactive").path("id").asText();
    db.update(
        "UPDATE tenants SET name=?,created_at='2026-10-01T00:00:00Z' WHERE id=CAST(? AS uuid)",
        "North Shop 50%_!",
        first);
    db.update(
        "UPDATE tenants SET name=?,created_at='2026-10-02T00:00:00Z' WHERE id=CAST(? AS uuid)",
        "North Market",
        second);
    db.update("UPDATE tenants SET name=? WHERE id=CAST(? AS uuid)", "North Shop 50%_!", foreign);
    db.update(
        "UPDATE tenant_members SET status='INACTIVE' WHERE tenant_id=CAST(? AS uuid) AND user_id=CAST(? AS uuid)",
        inactive,
        user.id());

    for (int page = 1; page <= 2; page++) {
      http.perform(
              authorized(
                  get("/api/v1/tenants")
                      .param("code", "SHOP")
                      .param("name", " north ")
                      .param("status", "ACTIVE")
                      .param("createdFrom", "2026-10-01T00:00:00Z")
                      .param("createdTo", "2026-10-03T00:00:00Z")
                      .param("sort", "createdAt:desc")
                      .param("page", String.valueOf(page))
                      .param("limit", "1"),
                  user.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.data[0].id").value(page == 1 ? second : first))
          .andExpect(jsonPath("$.pageInfo.total").value(2))
          .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
          .andExpect(jsonPath("$.pageInfo.hasNextPage").value(page == 1));
    }
    http.perform(authorized(get("/api/v1/tenants").param("id", first), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(first))
        .andExpect(jsonPath("$.pageInfo.total").value(1));
    http.perform(authorized(get("/api/v1/tenants").param("id", foreign), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.pageInfo.total").value(0));
    http.perform(
            authorized(
                get("/api/v1/tenants").param("code", "alp").param("name", "%_!"), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(first))
        .andExpect(jsonPath("$.pageInfo.total").value(1));
    http.perform(
            authorized(
                get("/api/v1/tenants")
                    .param("createdFrom", "2026-10-01T00:00:00Z")
                    .param("createdTo", "2026-10-02T00:00:00Z"),
                user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(first))
        .andExpect(jsonPath("$.pageInfo.total").value(1));
    for (var filter :
        List.of(
            Map.entry("status", "SUSPENDED"),
            Map.entry("name", "' OR 1=1 --"),
            Map.entry("id", inactive))) {
      http.perform(
              authorized(
                  get("/api/v1/tenants").param(filter.getKey(), filter.getValue()), user.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(0))
          .andExpect(jsonPath("$.pageInfo.total").value(0));
    }
  }

  @Test
  void tenantListSupportsAllowedSortFieldsAndStableTieBreaks() throws Exception {
    Account user = register("sort-user@example.com");
    String first = createTenant(user, "sort-alpha").path("id").asText();
    String second = createTenant(user, "sort-beta").path("id").asText();
    db.update(
        "UPDATE tenants SET name='Same name',created_at='2026-10-01T00:00:00Z' WHERE id IN (CAST(? AS uuid),CAST(? AS uuid))",
        first,
        second);
    for (String field : List.of("name", "status", "createdAt")) {
      for (String direction : List.of("asc", "DESC")) {
        http.perform(
                authorized(
                    get("/api/v1/tenants")
                        .param("sort", field + ":" + direction)
                        .param("limit", "1"),
                    user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(first));
        http.perform(
                authorized(
                    get("/api/v1/tenants")
                        .param("sort", field + ":" + direction)
                        .param("page", "2")
                        .param("limit", "1"),
                    user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(second));
      }
    }
    for (String field : List.of("id", "code")) {
      http.perform(authorized(get("/api/v1/tenants").param("sort", field + ":desc"), user.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").value(second))
          .andExpect(jsonPath("$.data[1].id").value(first));
    }
  }

  @Test
  void tenantListRejectsInvalidSortAndFilterValues() throws Exception {
    Account user = register("invalid-filter@example.com");
    for (var parameter :
        List.of(
            Map.entry("sort", "name; DROP TABLE tenants:asc"),
            Map.entry("sort", "name:invalid"),
            Map.entry("sort", "createdAt"),
            Map.entry("sort", "createdAt:desc:extra"),
            Map.entry("sort", ":desc"),
            Map.entry("sort", "name:"),
            Map.entry("id", "invalid"),
            Map.entry("status", "invalid"),
            Map.entry("createdFrom", "invalid"),
            Map.entry("name", "x".repeat(201)),
            Map.entry("code", "x".repeat(51)))) {
      http.perform(
              authorized(
                  get("/api/v1/tenants").param(parameter.getKey(), parameter.getValue()),
                  user.token()))
          .andExpect(status().isBadRequest());
    }
    http.perform(
            authorized(
                get("/api/v1/tenants")
                    .param("createdFrom", "2026-10-02T00:00:00Z")
                    .param("createdTo", "2026-10-01T00:00:00Z"),
                user.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void startupDoesNotRegisterAnAutomaticFlywayMigrator() {
    assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
  }

  @Test
  void openApiSpecificationIsPublicAndDocumentsAuthenticationRoutes() throws Exception {
    http.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
        .andExpect(jsonPath("$.info.title").value("ViShop Platform REST API"))
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/switch-tenant'].post").exists())
        .andExpect(jsonPath("$.components.schemas.AuthResponse").doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.AuthenticationView.properties")
                .value(org.hamcrest.Matchers.aMapWithSize(4)))
        .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.requestBody").doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.SwitchTenantRequest.properties")
                .value(org.hamcrest.Matchers.aMapWithSize(1)))
        .andExpect(
            jsonPath("$.components.securitySchemes.accessCookie.name")
                .value(AuthCookies.ACCESS_TOKEN))
        .andExpect(
            jsonPath("$.components.schemas.RegisterUserRequest.properties")
                .value(org.hamcrest.Matchers.aMapWithSize(2)))
        .andExpect(
            jsonPath("$.components.schemas.RegisterUserRequest.required")
                .value(org.hamcrest.Matchers.containsInAnyOrder("email", "password")));
  }

  @Test
  void loginSelectsAndRemembersOnlyTenantButLeavesAccountsWithoutTenantsUnselected()
      throws Exception {
    Account owner = register("initial-tenant@example.com");
    assertThat(login(owner).path("tenantId").isNull()).isTrue();
    assertThat(lastTenant(owner)).isNull();

    String tenant = createTenant(owner, "initial-tenant").path("id").asText();
    var selected = login(owner);
    assertThat(selected.path("tenantId").asText()).isEqualTo(tenant);
    assertThat(lastTenant(owner)).isEqualTo(tenant);
    http.perform(
            authorized(get("/api/v1/tenants/" + tenant + "/members/me"), selected.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void loginRequiresFirstSelectionThenRemembersSuccessfulSwitchAcrossLogins() throws Exception {
    Account owner = register("remember-tenant@example.com");
    String first = createTenant(owner, "remember-first").path("id").asText();
    String second = createTenant(owner, "remember-second").path("id").asText();
    assertThat(login(owner).path("tenantId").isNull()).isTrue();
    assertThat(lastTenant(owner)).isNull();

    var firstSelection = select(owner, first);
    assertThat(lastTenant(owner)).isEqualTo(first);
    assertThat(login(owner).path("tenantId").asText()).isEqualTo(first);
    select(owner, second);
    assertThat(lastTenant(owner)).isEqualTo(second);
    assertThat(login(owner).path("tenantId").asText()).isEqualTo(second);

    // Refresh preserves the token's tenant and must not overwrite the shared preference.
    var refreshed =
        cookieAuth(
            http.perform(refreshRequest(firstSelection.refreshToken()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(refreshed.path("tenantId").asText()).isEqualTo(first);
    assertThat(lastTenant(owner)).isEqualTo(second);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"membership", "tenant", "removed"})
  void loginDiscardsUnavailableRememberedTenantAndFallsBackToOnlyActiveTenant(String unavailable)
      throws Exception {
    Account owner = register("stale-tenant@example.com");
    String first = createTenant(owner, "stale-first").path("id").asText();
    String second = createTenant(owner, "stale-second").path("id").asText();
    select(owner, first);
    if (unavailable.equals("membership")) {
      db.update(
          "UPDATE tenant_members SET status='INACTIVE' WHERE tenant_id=CAST(? AS uuid) AND user_id=CAST(? AS uuid)",
          first,
          owner.id());
    } else if (unavailable.equals("removed")) {
      db.update(
          "DELETE FROM tenant_members WHERE tenant_id=CAST(? AS uuid) AND user_id=CAST(? AS uuid)",
          first,
          owner.id());
    } else {
      db.update("UPDATE tenants SET status='SUSPENDED' WHERE id=CAST(? AS uuid)", first);
    }
    assertThat(login(owner).path("tenantId").asText()).isEqualTo(second);
    assertThat(lastTenant(owner)).isEqualTo(second);
  }

  @Test
  void stalePreferenceIsClearedWhenMultipleOrNoActiveTenantsRemain() throws Exception {
    Account owner = register("clear-tenant@example.com");
    String first = createTenant(owner, "clear-first").path("id").asText();
    createTenant(owner, "clear-second");
    createTenant(owner, "clear-third");
    select(owner, first);
    db.update("UPDATE tenant_members SET status='INACTIVE' WHERE tenant_id=CAST(? AS uuid)", first);
    assertThat(login(owner).path("tenantId").isNull()).isTrue();
    assertThat(lastTenant(owner)).isNull();

    db.update(
        "UPDATE users SET last_tenant_id=CAST(? AS uuid) WHERE id=CAST(? AS uuid)",
        first,
        owner.id());
    db.update(
        "UPDATE tenant_members SET status='INACTIVE' WHERE user_id=CAST(? AS uuid)", owner.id());
    assertThat(login(owner).path("tenantId").isNull()).isTrue();
    assertThat(lastTenant(owner)).isNull();
  }

  @Test
  void rejectedTenantSwitchDoesNotOverwriteRememberedTenant() throws Exception {
    Account owner = register("reject-switch@example.com");
    Account stranger = register("foreign-tenant@example.com");
    String own = createTenant(owner, "reject-own").path("id").asText();
    String foreign = createTenant(stranger, "reject-foreign").path("id").asText();
    select(owner, own);
    http.perform(
            payload(
                authorized(
                    post("/api/v1/auth/switch-tenant")
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, owner.refreshToken())),
                    owner.token()),
                Map.of("tenantId", foreign)))
        .andExpect(status().isForbidden());
    assertThat(lastTenant(owner)).isEqualTo(own);
  }

  private CookieAuth login(Account account) throws Exception {
    return cookieAuth(
        http.perform(
                payload(
                    post("/api/v1/auth/login"),
                    Map.of("email", account.email(), "password", "StrongPass123!")))
            .andExpect(status().isOk())
            .andReturn());
  }

  private String lastTenant(Account account) {
    return db.queryForObject(
        "SELECT last_tenant_id::text FROM users WHERE id=CAST(? AS uuid)",
        String.class,
        account.id());
  }

  @Test
  void tenantMemberDetailsReturnUserAndPermissionsForTheRequestedTenant() throws Exception {
    Account owner = register("member-details-owner@example.com");
    Account member = register("member-details@example.com");
    String first = createTenant(owner, "member-details-first").path("id").asText();
    String second = createTenant(owner, "member-details-second").path("id").asText();
    String operations = role(first, "OPERATIONS");
    accept(member, invite(owner, first, member.email(), operations));
    accept(member, invite(owner, second, member.email(), role(second, "ACCOUNTING")));
    JsonNode details =
        body(
            http.perform(
                    authorized(
                        get("/api/v1/tenants/" + first + "/members/" + member.id()), owner.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tenantId").value(first))
                .andExpect(jsonPath("$.userId").value(member.id()))
                .andExpect(jsonPath("$.email").value(member.email()))
                .andExpect(jsonPath("$.fullName").isEmpty())
                .andExpect(jsonPath("$.roleId").value(operations))
                .andExpect(jsonPath("$.role").value("OPERATIONS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andReturn());
    assertThat(details.path("id").asText())
        .isEqualTo(
            db.queryForObject(
                "SELECT id::text FROM tenant_members WHERE tenant_id=CAST(? AS uuid) AND user_id=CAST(? AS uuid)",
                String.class,
                first,
                member.id()));
    assertThat(List.of(json.convertValue(details.path("permissions"), String[].class)))
        .containsExactlyInAnyOrderElementsOf(
            db.queryForList(
                "SELECT permission_key FROM role_permissions WHERE role_id=CAST(? AS uuid)",
                String.class,
                operations));
    http.perform(authorized(get("/api/v1/tenants/" + second + "/members/me"), member.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value(second))
        .andExpect(jsonPath("$.role").value("ACCOUNTING"));
  }

  @Test
  void memberProjectionKeepsSameNamedRolesSeparateAcrossTenants() throws Exception {
    Account owner = register("projection-owner@example.com");
    Account member = register("projection-member@example.com");
    String first = createTenant(owner, "projection-first").path("id").asText();
    String second = createTenant(owner, "projection-second").path("id").asText();
    var permissionsByTenant = new LinkedHashMap<String, String>();
    permissionsByTenant.put(first, "order.view");
    permissionsByTenant.put(second, "product.view");
    var roleIds = new HashMap<String, String>();
    for (var entry : permissionsByTenant.entrySet()) {
      JsonNode createdRole =
          body(
              http.perform(
                      payload(
                          authorized(
                              post("/api/v1/tenants/" + entry.getKey() + "/roles"), owner.token()),
                          Map.of("name", "Reader", "permissions", List.of(entry.getValue()))))
                  .andExpect(status().isCreated())
                  .andReturn());
      String roleId = createdRole.path("id").asText();
      roleIds.put(entry.getKey(), roleId);
      accept(member, invite(owner, entry.getKey(), member.email(), roleId));
    }
    select(member, first);
    for (var entry : permissionsByTenant.entrySet()) {
      http.perform(
              authorized(get("/api/v1/tenants/" + entry.getKey() + "/members/me"), member.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.tenantId").value(entry.getKey()))
          .andExpect(jsonPath("$.userId").value(member.id()))
          .andExpect(jsonPath("$.roleId").value(roleIds.get(entry.getKey())))
          .andExpect(jsonPath("$.role").value("Reader"))
          .andExpect(jsonPath("$.permissions.length()").value(1))
          .andExpect(jsonPath("$.permissions[0]").value(entry.getValue()));
    }
  }

  @Test
  void memberCanReadOwnEmptyRoleAndSeesRoleChangesWithoutANewToken() throws Exception {
    Account owner = register("empty-role-owner@example.com");
    Account member = register("empty-role-member@example.com");
    String tenant = createTenant(owner, "empty-role-tenant").path("id").asText();
    JsonNode custom =
        body(
            http.perform(
                    payload(
                        authorized(post("/api/v1/tenants/" + tenant + "/roles"), owner.token()),
                        Map.of("name", "Observer", "permissions", List.of())))
                .andExpect(status().isCreated())
                .andReturn());
    String roleId = custom.path("id").asText();
    accept(member, invite(owner, tenant, member.email(), roleId));
    for (String user : List.of("me", member.id())) {
      http.perform(
              authorized(get("/api/v1/tenants/" + tenant + "/members/" + user), member.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value("Observer"))
          .andExpect(jsonPath("$.permissions").isEmpty());
    }
    http.perform(
            authorized(get("/api/v1/tenants/" + tenant + "/members/" + owner.id()), member.token()))
        .andExpect(status().isForbidden());
    http.perform(
            payload(
                authorized(put("/api/v1/tenants/" + tenant + "/roles/" + roleId), owner.token()),
                Map.of("name", "Order reader", "permissions", List.of("order.view"))))
        .andExpect(status().isOk());
    http.perform(authorized(get("/api/v1/tenants/" + tenant + "/members/me"), member.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Order reader"))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0]").value("order.view"));
  }

  @Test
  void memberDetailsRequireMembershipAndMemberViewForOtherUsers() throws Exception {
    Account owner = register("details-access-owner@example.com");
    Account viewer = register("details-viewer@example.com");
    Account outsider = register("details-outsider@example.com");
    String tenant = createTenant(owner, "details-access").path("id").asText();
    JsonNode role =
        body(
            http.perform(
                    payload(
                        authorized(post("/api/v1/tenants/" + tenant + "/roles"), owner.token()),
                        Map.of(
                            "name", "Member viewer", "permissions", List.of("tenant.member.view"))))
                .andExpect(status().isCreated())
                .andReturn());
    accept(viewer, invite(owner, tenant, viewer.email(), role.path("id").asText()));
    http.perform(
            authorized(get("/api/v1/tenants/" + tenant + "/members/" + owner.id()), viewer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("OWNER"));
    http.perform(
            authorized(
                get("/api/v1/tenants/" + tenant + "/members/" + owner.id()), outsider.token()))
        .andExpect(status().isForbidden());
    http.perform(authorized(get("/api/v1/tenants/" + tenant + "/members/me"), outsider.token()))
        .andExpect(status().isForbidden());
    String otherTenant = createTenant(outsider, "details-other").path("id").asText();
    http.perform(
            authorized(
                get("/api/v1/tenants/" + otherTenant + "/members/" + outsider.id()), owner.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberDetailsDoNotFindUsersFromAnotherTenant() throws Exception {
    Account owner = register("scoped-details-owner@example.com");
    Account member = register("scoped-details-member@example.com");
    String first = createTenant(owner, "scoped-details-first").path("id").asText();
    String second = createTenant(owner, "scoped-details-second").path("id").asText();
    accept(member, invite(owner, second, member.email(), role(second, "WAREHOUSE")));
    for (String user : List.of(member.id(), UUID.randomUUID().toString())) {
      http.perform(authorized(get("/api/v1/tenants/" + first + "/members/" + user), owner.token()))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
  }

  @Test
  void inactiveMembersCannotReadTheirPermissionsAndManagersCanSeeTheirStatus() throws Exception {
    Account owner = register("inactive-details-owner@example.com");
    Account member = register("inactive-details-member@example.com");
    String tenant = createTenant(owner, "inactive-details").path("id").asText();
    accept(member, invite(owner, tenant, member.email(), role(tenant, "WAREHOUSE")));
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + member.id()), owner.token()))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/tenants/" + tenant + "/members/me"), member.token()))
        .andExpect(status().isForbidden());
    http.perform(
            authorized(get("/api/v1/tenants/" + tenant + "/members/" + member.id()), owner.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    db.update("UPDATE tenants SET status='SUSPENDED' WHERE id=CAST(? AS uuid)", tenant);
    http.perform(authorized(get("/api/v1/tenants/" + tenant + "/members/me"), owner.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberDetailsRequireAuthenticationAndValidIdentifiers() throws Exception {
    http.perform(get("/api/v1/tenants/" + UUID.randomUUID() + "/members/me"))
        .andExpect(status().isUnauthorized());
    Account owner = register("invalid-details-owner@example.com");
    String tenant = createTenant(owner, "invalid-details").path("id").asText();
    http.perform(
            authorized(get("/api/v1/tenants/" + tenant + "/members/not-a-uuid"), owner.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void accessCookieAuthenticatesAndRefreshRequiresCookieInsteadOfJson() throws Exception {
    Account user = register("cookie-auth@example.com");
    http.perform(
            get("/api/v1/auth/me")
                .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, user.token()))
                .header("Origin", "http://localhost:3000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.id()))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    http.perform(
            get("/api/v1/auth/me")
                .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, user.refreshToken())))
        .andExpect(status().isUnauthorized());
    http.perform(payload(post("/api/v1/auth/refresh"), Map.of("refreshToken", user.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cookieWritesRequireCustomHeaderAndTrustedOrigin() throws Exception {
    Account user = register("csrf@example.com");
    http.perform(
            post("/api/v1/auth/logout").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, user.token())))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, user.refreshToken())))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("email", user.email(), "password", "StrongPass123!"))))
        .andExpect(status().isForbidden());
    http.perform(refreshRequest(user.refreshToken()).header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
    http.perform(
            options("/api/v1/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-requested-with"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void logoutClearsCookiesEvenWhenAccessExpiredOrMissing() throws Exception {
    Account user = register("expired-logout@example.com");
    var expiredProvider =
        new io.github.coreplatform.common.security.jwt.JwtTokenProvider(
            "test-jwt-secret-with-at-least-thirty-two-bytes", -1000);
    String expired =
        expiredProvider.generateIamAccessToken(
            UUID.fromString(user.id()), null, SystemRole.USER.name(), null, Set.of());
    for (var access : List.of(expired, "")) {
      var result =
          http.perform(
                  post("/api/v1/auth/logout")
                      .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE)
                      .cookie(
                          new Cookie(AuthCookies.ACCESS_TOKEN, access),
                          new Cookie(AuthCookies.REFRESH_TOKEN, user.refreshToken())))
              .andExpect(status().isNoContent())
              .andReturn();
      assertThat(result.getResponse().getHeaders("Set-Cookie"))
          .hasSize(2)
          .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0", "HttpOnly"));
    }
  }

  @Test
  void registrationIsGlobalAndDoesNotPersistTokens() throws Exception {
    Account user = register("User@example.com");
    assertThat(db.queryForObject("SELECT count(*) FROM tenants", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT system_role FROM users", String.class))
        .isEqualTo(SystemRole.USER.name());
    assertThat(db.queryForObject("SELECT full_name FROM users", String.class)).isNull();
    assertThat(db.queryForObject("SELECT password_hash FROM users", String.class))
        .startsWith("$2a$")
        .isNotEqualTo("StrongPass123!");
    assertThat(tokens.parseClaims(user.token())).doesNotContainKeys("sid", "sv");
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name='sessions'",
                Integer.class))
        .isZero();
    http.perform(authorized(get("/api/v1/auth/me"), user.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.systemRole").value(SystemRole.USER.name()))
        .andExpect(jsonPath("$.fullName").isEmpty())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.tenantId").doesNotExist());
    http.perform(
            payload(
                post("/api/v1/auth/register"),
                Map.of("email", "user@EXAMPLE.com", "password", "StrongPass123!")))
        .andExpect(status().isConflict());
    http.perform(
            payload(
                post("/api/v1/auth/login"),
                Map.of("email", "USER@example.com", "password", "StrongPass123!")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").isEmpty());
    assertThat(
            db.queryForObject(
                "SELECT string_agg(payload::text,',') FROM outbox_events", String.class))
        .doesNotContain("StrongPass123!", user.refreshToken());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"fullName", "tenantId", "role", "systemRole"})
  void registrationRejectsFieldsOutsideEmailAndPassword(String field) throws Exception {
    http.perform(
            payload(
                post("/api/v1/auth/register"),
                Map.of(
                    "email", "evil@example.com", "password", "StrongPass123!", field, "injected")))
        .andExpect(status().isBadRequest());
    assertThat(db.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @Test
  void registrationStillRequiresValidEmailAndPassword() throws Exception {
    for (var request :
        List.of(
            Map.of("email", "invalid-email", "password", "StrongPass123!"),
            Map.of("email", "invalid@example.com", "password", "short"),
            Map.of("email", "invalid@example.com"),
            Map.of("password", "StrongPass123!"))) {
      http.perform(payload(post("/api/v1/auth/register"), request))
          .andExpect(status().isUnprocessableEntity());
    }
    http.perform(
            payload(
                post("/api/v1/auth/register"),
                Map.of("email", "invalid@example.com", "password", "alllowercase")))
        .andExpect(status().isBadRequest());
    assertThat(db.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @Test
  void creatingTenantIsAtomicAndAnotherUsersTenantIsForbidden() throws Exception {
    Account owner = register("owner@example.com");
    String tenant = createTenant(owner, "first-tenant").path("id").asText();
    assertThat(db.queryForObject("SELECT count(*) FROM roles", Integer.class)).isEqualTo(4);
    assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id=CAST(? AS uuid)",
                Integer.class,
                role(tenant, "OWNER")))
        .isEqualTo(34);
    http.perform(authorized(get("/api/v1/tenants/" + tenant), owner.token()))
        .andExpect(status().isOk());
    http.perform(
            payload(
                authorized(post("/api/v1/tenants"), owner.token()),
                Map.of("code", "first-tenant", "name", "Duplicate")))
        .andExpect(status().isConflict());
    assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class))
        .isEqualTo(1);
    Account other = register("other@example.com");
    http.perform(authorized(get("/api/v1/tenants/" + tenant), other.token()))
        .andExpect(status().isForbidden());
    http.perform(
            payload(
                authorized(
                    post("/api/v1/auth/switch-tenant")
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, other.refreshToken())),
                    other.token()),
                Map.of("tenantId", tenant)))
        .andExpect(status().isForbidden());
  }

  @Test
  void oneUserCanHaveDifferentRolesInTwoTenants() throws Exception {
    Account owner = register("owner@example.com");
    Account member = register("member@example.com");
    String first = createTenant(owner, "first-tenant").path("id").asText();
    String second = createTenant(owner, "second-tenant").path("id").asText();
    accept(member, invite(owner, first, member.email(), role(first, "OPERATIONS")));
    accept(member, invite(owner, second, member.email(), role(second, "ACCOUNTING")));
    var operations = select(member, first);
    assertThat(operations.path("permissions").toString())
        .contains("order.create")
        .doesNotContain("finance.reconciliation");
    var accounting = select(member, second);
    assertThat(accounting.path("permissions").toString())
        .contains("finance.reconciliation")
        .doesNotContain("order.create");
    http.perform(authorized(get("/api/v1/tenants"), member.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
    // Previously issued refresh JWTs retain their signed tenant after cookies are replaced.
    var result =
        http.perform(refreshRequest(operations.refreshToken()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(body(result).path("tenantId").asText()).isEqualTo(first);
  }

  @Test
  void unrelatedCookiesAndUntrustedHeadersNeverAuthenticate() throws Exception {
    Account user = register("user@example.com");
    http.perform(
            get("/api/v1/auth/me")
                .header("X-User-Id", user.id())
                .header("X-User-Roles", SystemRole.PLATFORM_ADMIN.name()))
        .andExpect(status().isUnauthorized());
    http.perform(get("/api/v1/auth/me").cookie(new Cookie("vishop_session", user.refreshToken())))
        .andExpect(status().isUnauthorized());
    http.perform(authorized(get("/api/v1/auth/me"), user.refreshToken()))
        .andExpect(status().isUnauthorized());
    http.perform(
            authorized(post("/api/v1/auth/logout"), user.token())
                .header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void cmsUsesSystemRoleAndDoesNotGrantTenantMembership() throws Exception {
    Account owner = register("owner@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    http.perform(authorized(get("/api/v1/cms/tenants"), owner.token()))
        .andExpect(status().isForbidden());
    Account admin = register("admin@example.com");
    db.update("UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id=CAST(? AS uuid)", admin.id());
    http.perform(authorized(get("/api/v1/cms/tenants"), admin.token())).andExpect(status().isOk());
    http.perform(authorized(get("/api/v1/tenants/" + tenant), admin.token()))
        .andExpect(status().isForbidden());
    http.perform(
            payload(
                authorized(
                    patch("/api/v1/cms/users/" + admin.id() + "/system-role"), admin.token()),
                Map.of("systemRole", SystemRole.USER.name())))
        .andExpect(status().isConflict());
    http.perform(
            payload(
                authorized(
                    patch("/api/v1/cms/users/" + owner.id() + "/system-role"), admin.token()),
                Map.of("systemRole", SystemRole.PLATFORM_ADMIN.name())))
        .andExpect(status().isNoContent());
    http.perform(
            payload(
                authorized(
                    patch("/api/v1/cms/users/" + admin.id() + "/system-role"), admin.token()),
                Map.of("systemRole", SystemRole.USER.name())))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/cms/tenants"), admin.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void statelessLogoutClearsBothCookiesAndAudits() throws Exception {
    Account user = register("logout@example.com");
    var loggedOut =
        http.perform(authorized(post("/api/v1/auth/logout"), user.token()))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(loggedOut.getResponse().getHeaders("Set-Cookie"))
        .hasSize(2)
        .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0", "HttpOnly", "SameSite=Lax"));
    http.perform(authorized(get("/api/v1/auth/me"), user.token())).andExpect(status().isOk());
    http.perform(refreshRequest(user.refreshToken())).andExpect(status().isOk());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='UserLoggedOut'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void suspendedUsersAndTenantsCannotIssueNewTokens() throws Exception {
    Account admin = register("admin@example.com");
    Account owner = register("owner@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    String selected = select(owner, tenant).accessToken();
    db.update("UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id=CAST(? AS uuid)", admin.id());
    http.perform(
            payload(
                authorized(patch("/api/v1/cms/tenants/" + tenant + "/status"), admin.token()),
                Map.of("status", "SUSPENDED")))
        .andExpect(status().isOk());
    http.perform(
            payload(
                authorized(
                    post("/api/v1/auth/switch-tenant")
                        .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, owner.refreshToken())),
                    selected),
                Map.of("tenantId", tenant)))
        .andExpect(status().isForbidden());
    var resolved = queries.dispatch(new AuthenticateQuery(selected, true));
    assertThat(resolved.tenantId()).isEqualTo(UUID.fromString(tenant));
    http.perform(
            payload(
                authorized(patch("/api/v1/cms/users/" + owner.id() + "/status"), admin.token()),
                Map.of("status", "SUSPENDED")))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/auth/me"), owner.token()))
        .andExpect(status().isUnauthorized());
    http.perform(
            payload(
                authorized(patch("/api/v1/cms/users/" + owner.id() + "/status"), admin.token()),
                Map.of("status", "ACTIVE")))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/auth/me"), owner.token())).andExpect(status().isOk());
    http.perform(
            payload(
                post("/api/v1/auth/login"),
                Map.of("email", owner.email(), "password", "StrongPass123!")))
        .andExpect(status().isOk());
  }

  @Test
  void memberRemovalAndRoleChangesInvalidateOldGrants() throws Exception {
    Account owner = register("owner@example.com");
    Account member = register("member@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    accept(member, invite(owner, tenant, member.email(), role(tenant, "OPERATIONS")));
    var selectedTokens = select(member, tenant);
    String old = selectedTokens.accessToken();
    String refresh = selectedTokens.refreshToken();
    http.perform(
            payload(
                authorized(
                    put("/api/v1/tenants/" + tenant + "/members/" + member.id() + "/role"),
                    owner.token()),
                Map.of("roleId", role(tenant, "ACCOUNTING"))))
        .andExpect(status().isNoContent());
    var refreshed =
        cookieAuth(http.perform(refreshRequest(refresh)).andExpect(status().isOk()).andReturn());
    assertThat(refreshed.path("permissions").toString())
        .doesNotContain("order.create")
        .contains("finance.reconciliation");
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + member.id()), owner.token()))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/tenants/" + tenant), old))
        .andExpect(status().isForbidden());
    http.perform(refreshRequest(refresh)).andExpect(status().isForbidden());
  }

  @Test
  void invitationsAreScopedEmailBoundAndSingleUse() throws Exception {
    Account owner = register("owner@example.com");
    Account member = register("member@example.com");
    Account wrong = register("wrong@example.com");
    String first = createTenant(owner, "first-tenant").path("id").asText();
    String second = createTenant(owner, "second-tenant").path("id").asText();
    http.perform(
            payload(
                authorized(post("/api/v1/tenants/" + first + "/invitations"), owner.token()),
                Map.of("email", member.email(), "roleId", role(second, "OWNER"))))
        .andExpect(status().isNotFound());
    String token = invite(owner, first, member.email(), role(first, "WAREHOUSE"));
    assertThat(db.queryForObject("SELECT token_hash FROM tenant_invitations", String.class))
        .hasSize(64)
        .isNotEqualTo(token);
    http.perform(
            payload(
                authorized(post("/api/v1/invitations/accept"), wrong.token()),
                Map.of("token", token)))
        .andExpect(status().isForbidden());
    accept(member, token);
    http.perform(
            payload(
                authorized(post("/api/v1/invitations/accept"), member.token()),
                Map.of("token", token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tenantRoleCannotImpersonateSystemAdminOrGrantMorePermissions() throws Exception {
    Account owner = register("owner@example.com");
    Account member = register("member@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    var limited =
        body(
            http.perform(
                    payload(
                        authorized(post("/api/v1/tenants/" + tenant + "/roles"), owner.token()),
                        Map.of(
                            "name",
                            SystemRole.PLATFORM_ADMIN.name(),
                            "permissions",
                            List.of(
                                "tenant.view", "tenant.member.role_assign", "tenant.member.view"))))
                .andExpect(status().isCreated())
                .andReturn());
    accept(member, invite(owner, tenant, member.email(), limited.path("id").asText()));
    String token = select(member, tenant).accessToken();
    http.perform(authorized(get("/api/v1/cms/users"), token)).andExpect(status().isForbidden());
    http.perform(
            payload(
                authorized(post("/api/v1/tenants/" + tenant + "/roles"), token),
                Map.of("name", "Escalation", "permissions", List.of("order.create"))))
        .andExpect(status().isForbidden());
    http.perform(
            payload(
                authorized(post("/api/v1/tenants/" + tenant + "/roles"), owner.token()),
                Map.of("name", "Unknown", "permissions", List.of("system.admin"))))
        .andExpect(status().isBadRequest());
    http.perform(
            payload(
                authorized(
                    put("/api/v1/tenants/" + tenant + "/members/" + member.id() + "/role"), token),
                Map.of("roleId", role(tenant, "OWNER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void finalOwnerCannotBeRemovedOrDemotedEvenWithConcurrentRequests() throws Exception {
    Account first = register("first@example.com");
    Account second = register("second@example.com");
    String tenant = createTenant(first, "owned-tenant").path("id").asText();
    String warehouse = role(tenant, "WAREHOUSE");
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + first.id()), first.token()))
        .andExpect(status().isConflict());
    accept(second, invite(first, tenant, second.email(), role(tenant, "OWNER")));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (Account actor : List.of(first, second))
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return http.perform(
                          payload(
                              authorized(
                                  put(
                                      "/api/v1/tenants/"
                                          + tenant
                                          + "/members/"
                                          + actor.id()
                                          + "/role"),
                                  actor.token()),
                              Map.of("roleId", warehouse)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  futures.get(0).get(10, TimeUnit.SECONDS),
                  futures.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(204, 409);
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void removedIntrospectionEndpointIsNotExposed() throws Exception {
    Account user = register("user@example.com");
    http.perform(
            payload(
                authorized(post("/internal/auth/introspect"), user.token()),
                Map.of("credential", user.token(), "type", "BEARER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void refreshRequiresRefreshJwtAndPreservesAbsoluteExpiry() throws Exception {
    Account user = register("refresh@example.com");
    http.perform(refreshRequest(user.token())).andExpect(status().isUnauthorized());
    http.perform(
            post("/api/v1/auth/refresh")
                .header(AuthCookies.REQUEST_HEADER, AuthCookies.REQUEST_HEADER_VALUE))
        .andExpect(status().isUnauthorized());
    var refreshed =
        cookieAuth(
            http.perform(refreshRequest(user.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn());
    assertThat(tokens.parseClaims(refreshed.refreshToken()).getExpiration())
        .isEqualTo(tokens.parseClaims(user.refreshToken()).getExpiration());
    assertThat(refreshed.refreshToken()).isNotEqualTo(user.refreshToken());
    String expired =
        tokens.generateRefreshToken(
            UUID.fromString(user.id()), null, java.time.Instant.now().minusSeconds(1));
    http.perform(refreshRequest(expired)).andExpect(status().isUnauthorized());
    String foreign =
        tokens.generateRefreshToken(
            UUID.randomUUID(), null, java.time.Instant.now().plusSeconds(100));
    http.perform(
            payload(
                post("/api/v1/auth/switch-tenant")
                    .header("Authorization", "Bearer " + user.token())
                    .cookie(new Cookie(AuthCookies.REFRESH_TOKEN, foreign)),
                Map.of("tenantId", UUID.randomUUID())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredAccessDoesNotPreventRefresh() throws Exception {
    Account user = register("expired-access@example.com");
    var expiredProvider =
        new io.github.coreplatform.common.security.jwt.JwtTokenProvider(
            "test-jwt-secret-with-at-least-thirty-two-bytes", -1000);
    String expired =
        expiredProvider.generateIamAccessToken(
            UUID.fromString(user.id()), null, SystemRole.USER.name(), null, Set.of());
    http.perform(authorized(get("/api/v1/auth/me"), expired)).andExpect(status().isUnauthorized());
    var result =
        http.perform(
                refreshRequest(user.refreshToken())
                    .cookie(new Cookie(AuthCookies.ACCESS_TOKEN, expired)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(cookieAuth(result).accessToken()).isNotBlank();
  }

  @Test
  void inactiveOwnerCanBeRemovedWhileLastActiveOwnerRemainsProtected() throws Exception {
    Account owner = register("owner@example.com");
    Account inactive = register("inactive@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    accept(inactive, invite(owner, tenant, inactive.email(), role(tenant, "OWNER")));
    db.update("UPDATE users SET status='INACTIVE' WHERE id=CAST(? AS uuid)", inactive.id());
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + inactive.id()), owner.token()))
        .andExpect(status().isNoContent());
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + owner.id()), owner.token()))
        .andExpect(status().isConflict());
  }

  @Test
  void featureMutationApisRequireAnExistingTransaction() {
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.vishop.iam.modules.tenant.application.api.TenantApi.class)
                    .create(UUID.randomUUID(), "transaction-required", "Shop"))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.vishop.iam.modules.access.application.api.AccessApi.class)
                    .initializeOwner(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.vishop.iam.modules.tenant.application.api.TenantApi.class)
                    .lockTenant(UUID.randomUUID()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void tenantCreationRollsBackJpaJdbcAndDefaultRolesWhenOutboxFails() throws Exception {
    Account owner = register("rollback@example.com");
    var user =
        context.getBean(UserRepositoryPort.class).user(UUID.fromString(owner.id())).orElseThrow();
    var actor = new AuthenticatedUser(user.id(), null, user.systemRole().name(), null, Set.of());
    // Stub the spy target so MANDATORY transaction advice does not run while configuring it.
    IamEventPublisherPort events =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(eventsSpy);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              assertThat(db.queryForObject("SELECT count(*) FROM tenants", Integer.class))
                  .isEqualTo(1);
              assertThat(db.queryForObject("SELECT count(*) FROM roles", Integer.class))
                  .isEqualTo(4);
              assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class))
                  .isZero();
              throw new IllegalStateException("Outbox unavailable");
            })
        .when(events)
        .publish(org.mockito.ArgumentMatchers.any());
    var handler = context.getBean(CreateTenantCommandHandler.class);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    assertThatThrownBy(
            () ->
                tx.execute(
                    status ->
                        handler.handle(new CreateTenantCommand(actor, "rollback-shop", "Shop"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Outbox unavailable");
    assertThat(db.queryForObject("SELECT count(*) FROM tenants", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM roles", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class)).isZero();
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_type='Tenant'", Integer.class))
        .isZero();
  }

  @Test
  void tenantOnboardingRollsBackAllModulesWhenOwnerInitializationFails() throws Exception {
    Account owner = register("owner-init-rollback@example.com");
    var user =
        context.getBean(UserRepositoryPort.class).user(UUID.fromString(owner.id())).orElseThrow();
    var actor = new AuthenticatedUser(user.id(), null, user.systemRole().name(), null, Set.of());
    var access = context.getBean(com.vishop.iam.modules.access.application.api.AccessApi.class);
    var failingAccess =
        org.mockito.Mockito.mock(com.vishop.iam.modules.access.application.api.AccessApi.class);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              access.initializeOwner(invocation.getArgument(0), invocation.getArgument(1));
              assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class))
                  .isEqualTo(1);
              assertThat(
                      db.queryForObject(
                          "SELECT count(*) FROM outbox_events WHERE aggregate_type='Tenant'",
                          Integer.class))
                  .isEqualTo(1);
              throw new IllegalStateException("Owner initialization failed");
            })
        .when(failingAccess)
        .initializeOwner(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    var handler =
        new CreateTenantCommandHandler(
            context.getBean(com.vishop.iam.modules.identity.application.api.IdentityApi.class),
            context.getBean(com.vishop.iam.modules.tenant.application.api.TenantApi.class),
            failingAccess);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    assertThatThrownBy(
            () ->
                tx.execute(
                    status ->
                        handler.handle(new CreateTenantCommand(actor, "owner-init-fail", "Shop"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Owner initialization failed");
    assertThat(db.queryForObject("SELECT count(*) FROM tenants", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM roles", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT count(*) FROM tenant_members", Integer.class)).isZero();
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_type='Tenant'", Integer.class))
        .isZero();
  }

  @Test
  void tenantMappingKeepsHttpContractAndPersistsAggregateChanges() throws Exception {
    Account owner = register("mapping@example.com");
    JsonNode tenant = createTenant(owner, "mapped-shop");
    Set<String> fields = new HashSet<>();
    tenant.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactlyInAnyOrder("id", "code", "name", "status", "createdAt");
    String id = tenant.path("id").asText();
    String createdAt =
        db.queryForObject(
            "SELECT created_at::text FROM tenants WHERE id=CAST(? AS uuid)", String.class, id);
    assertThat(
            db.queryForObject(
                "SELECT created_at=updated_at AND version=0 FROM tenants WHERE id=CAST(? AS uuid)",
                Boolean.class,
                id))
        .isTrue();
    // A fixed old value makes the lifecycle update check independent of clock resolution.
    db.update("UPDATE tenants SET updated_at='2000-01-01T00:00:00Z' WHERE id=CAST(? AS uuid)", id);
    for (int i = 1; i <= 2; i++) {
      http.perform(
              payload(
                  authorized(patch("/api/v1/tenants/" + id), owner.token()),
                  Map.of("name", "Name " + i)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Name " + i));
      assertThat(
              db.queryForObject(
                  "SELECT version FROM tenants WHERE id=CAST(? AS uuid)", Long.class, id))
          .isEqualTo((long) i);
      assertThat(
              db.queryForObject(
                  "SELECT created_at::text FROM tenants WHERE id=CAST(? AS uuid)",
                  String.class,
                  id))
          .isEqualTo(createdAt);
      assertThat(
              db.queryForObject(
                  "SELECT updated_at>=created_at FROM tenants WHERE id=CAST(? AS uuid)",
                  Boolean.class,
                  id))
          .isTrue();
    }
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='TenantRenamed'",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            db.queryForObject(
                "SELECT payload->'payload'->>'ownerId' FROM outbox_events WHERE event_type='TenantCreated'",
                String.class))
        .isEqualTo(owner.id());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE id::text <> payload->>'eventId'",
                Integer.class))
        .isZero();
  }

  @Test
  void versionRejectsStaleTenantUpdates() throws Exception {
    Account owner = register("stale-version@example.com");
    UUID id = UUID.fromString(createTenant(owner, "stale-version").path("id").asText());
    var repository =
        context.getBean(com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort.class);
    var first = repository.tenant(id);
    var stale = repository.tenant(id);
    first.rename("First update", UUID.fromString(owner.id()));
    stale.rename("Stale update", UUID.fromString(owner.id()));
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.executeWithoutResult(status -> repository.save(first));
    assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.save(stale)))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    assertThat(repository.tenant(id).name()).isEqualTo("First update");
  }

  @Test
  void outboxWaitsForKafkaAcknowledgementAndRetainsFailures() throws Exception {
    register("first@example.com");
    @SuppressWarnings("unchecked")
    org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
        org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
    org.mockito.Mockito.when(
            kafka.send(
                org.mockito.ArgumentMatchers
                    .<org.apache.kafka.clients.producer.ProducerRecord<String, String>>any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    var relay =
        new com.vishop.iam.infra.messaging.IamOutboxRelay(
            context.getBean(io.github.coreplatform.common.persistence.OutboxEventRepository.class),
            kafka);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.executeWithoutResult(status -> relay.publish());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status='PUBLISHED' AND published_at IS NOT NULL AND event_type='UserRegistered'",
                Integer.class))
        .isEqualTo(1);
    register("second@example.com");
    org.mockito.Mockito.when(
            kafka.send(
                org.mockito.ArgumentMatchers
                    .<org.apache.kafka.clients.producer.ProducerRecord<String, String>>any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));
    tx.executeWithoutResult(status -> relay.publish());
    assertThat(
            db.queryForObject(
                "SELECT retry_count FROM outbox_events WHERE status='PENDING' AND event_type='UserRegistered'",
                Integer.class))
        .isEqualTo(1);
    for (int i = 0; i < 4; i++) tx.executeWithoutResult(status -> relay.publish());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status='FAILED' AND published_at IS NULL AND event_type='UserRegistered'",
                Integer.class))
        .isEqualTo(1);
  }
}
