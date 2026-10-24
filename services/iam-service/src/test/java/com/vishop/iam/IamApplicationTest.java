package com.vishop.iam;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.composition.onboarding.application.command.CreateTenantCommand;
import com.vishop.iam.composition.onboarding.application.command.CreateTenantCommandHandler;
import com.vishop.iam.database.JpaDatabaseFixture;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.tenant.domain.port.TenantRepositoryPort;
import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
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
      "management.tracing.enabled=false",
      "vishop.security.jwt.secret=test-jwt-secret-with-at-least-thirty-two-bytes",
      "vishop.security.internal-secret=test-internal-secret-with-at-least-thirty-two-bytes"
    })
@AutoConfigureMockMvc
class IamApplicationTest {
  private static final String INTERNAL = "test-internal-secret-with-at-least-thirty-two-bytes";
  private static final EmbeddedPostgres POSTGRES = start();

  private static EmbeddedPostgres start() {
    try {
      return EmbeddedPostgres.builder().setPort(0).start();
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
  @Autowired ObjectMapper json;
  @Autowired EntityManager entityManager;
  JpaDatabaseFixture db;
  @Autowired io.github.coreplatform.common.security.jwt.JwtTokenProvider tokens;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired org.springframework.context.ApplicationContext context;

  @BeforeEach
  void reset() {
    db = new JpaDatabaseFixture(entityManager, transactions);
    db.execute("TRUNCATE users,tenants,outbox_events,consumed_messages CASCADE");
  }

  record Account(String email, String id, String token, String cookie) {}

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private MockHttpServletRequestBuilder payload(MockHttpServletRequestBuilder request, Object data)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
  }

  private Account register(String email) throws Exception {
    var result =
        http.perform(
                payload(
                    post("/api/v1/auth/register"),
                    Map.of("email", email, "password", "StrongPass123!", "fullName", "Test User")))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode response = body(result);
    assertThat(result.getResponse().getHeader("Set-Cookie"))
        .contains("HttpOnly", "Secure", "SameSite=Lax");
    String cookie =
        result
            .getResponse()
            .getHeader("Set-Cookie")
            .split(";", 2)[0]
            .substring("vishop_session=".length());
    return new Account(
        email,
        response.path("user").path("id").asText(),
        response.path("accessToken").asText(),
        cookie);
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

  private JsonNode select(Account account, String tenant) throws Exception {
    return body(
        http.perform(
                payload(
                    authorized(post("/api/v1/auth/switch-tenant"), account.token()),
                    Map.of("tenantId", tenant)))
            .andExpect(status().isOk())
            .andReturn());
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
                .andExpect(jsonPath("$.fullName").value("Test User"))
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
  void memberProjectionKeepsSameNamedRolesSeparateAcrossTenantsForCookieSessions()
      throws Exception {
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
              get("/api/v1/tenants/" + entry.getKey() + "/members/me")
                  .cookie(new Cookie("vishop_session", member.cookie())))
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
  void registrationIsGlobalAndStoresOnlySessionHashes() throws Exception {
    Account user = register("User@example.com");
    assertThat(db.queryForObject("SELECT count(*) FROM tenants", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT system_role FROM users", String.class)).isEqualTo("USER");
    assertThat(db.queryForObject("SELECT password_hash FROM users", String.class))
        .startsWith("$2a$")
        .isNotEqualTo("StrongPass123!");
    assertThat(db.queryForObject("SELECT token_hash FROM sessions", String.class))
        .hasSize(64)
        .isNotEqualTo(user.cookie());
    http.perform(get("/api/v1/auth/me").cookie(new Cookie("vishop_session", user.cookie())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.systemRole").value("USER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.tenantId").doesNotExist());
    http.perform(
            payload(
                post("/api/v1/auth/register"),
                Map.of(
                    "email",
                    "user@EXAMPLE.com",
                    "password",
                    "StrongPass123!",
                    "fullName",
                    "Other")))
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
        .doesNotContain("StrongPass123!", user.cookie());
  }

  @Test
  void registrationRejectsClientAssignedPrivileges() throws Exception {
    http.perform(
            payload(
                post("/api/v1/auth/register"),
                Map.of(
                    "email",
                    "evil@example.com",
                    "password",
                    "StrongPass123!",
                    "fullName",
                    "Evil",
                    "tenantId",
                    UUID.randomUUID(),
                    "role",
                    "PLATFORM_ADMIN")))
        .andExpect(status().isForbidden());
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
                authorized(post("/api/v1/auth/switch-tenant"), other.token()),
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
        .andExpect(jsonPath("$.length()").value(2));
    // Switching the cookie context does not overwrite a previous tab's signed tenant token.
    var result =
        http.perform(
                authorized(post("/api/v1/auth/refresh"), operations.path("accessToken").asText()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(body(result).path("tenantId").asText()).isEqualTo(first);
  }

  @Test
  void cookiesRequireOriginForWritesAndHeadersNeverAuthenticate() throws Exception {
    Account user = register("user@example.com");
    http.perform(
            get("/api/v1/auth/me")
                .header("X-User-Id", user.id())
                .header("X-User-Roles", "PLATFORM_ADMIN"))
        .andExpect(status().isUnauthorized());
    http.perform(post("/api/v1/auth/logout").cookie(new Cookie("vishop_session", user.cookie())))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie("vishop_session", user.cookie()))
                .header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
    http.perform(
            get("/api/v1/auth/me")
                .cookie(new Cookie("vishop_session", user.cookie()))
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    http.perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie("vishop_session", user.cookie()))
                .header("Origin", "http://localhost:3000"))
        .andExpect(status().isNoContent());
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
                Map.of("systemRole", "USER")))
        .andExpect(status().isConflict());
    http.perform(
            payload(
                authorized(
                    patch("/api/v1/cms/users/" + owner.id() + "/system-role"), admin.token()),
                Map.of("systemRole", "PLATFORM_ADMIN")))
        .andExpect(status().isNoContent());
    http.perform(
            payload(
                authorized(
                    patch("/api/v1/cms/users/" + admin.id() + "/system-role"), admin.token()),
                Map.of("systemRole", "USER")))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/cms/tenants"), admin.token()))
        .andExpect(status().isForbidden());
  }

  @Test
  void logoutRevokesBothCookieAndAlreadyIssuedJwt() throws Exception {
    Account user = register("user@example.com");
    http.perform(
            payload(
                post("/internal/auth/introspect").header("X-Iam-Internal-Key", INTERNAL),
                Map.of("credential", user.token(), "type", "BEARER")))
        .andExpect(status().isOk());
    http.perform(authorized(post("/api/v1/auth/logout"), user.token()))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/auth/me"), user.token()))
        .andExpect(status().isUnauthorized());
    http.perform(get("/api/v1/auth/me").cookie(new Cookie("vishop_session", user.cookie())))
        .andExpect(status().isUnauthorized());
    http.perform(
            payload(
                post("/internal/auth/introspect").header("X-Iam-Internal-Key", INTERNAL),
                Map.of("credential", user.token(), "type", "BEARER")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void suspendedUsersAndTenantsLoseAccessImmediately() throws Exception {
    Account admin = register("admin@example.com");
    Account owner = register("owner@example.com");
    String tenant = createTenant(owner, "owned-tenant").path("id").asText();
    String selected = select(owner, tenant).path("accessToken").asText();
    db.update("UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id=CAST(? AS uuid)", admin.id());
    http.perform(
            payload(
                authorized(patch("/api/v1/cms/tenants/" + tenant + "/status"), admin.token()),
                Map.of("status", "SUSPENDED")))
        .andExpect(status().isOk());
    http.perform(
            payload(
                authorized(post("/api/v1/auth/switch-tenant"), selected),
                Map.of("tenantId", tenant)))
        .andExpect(status().isForbidden());
    var resolved =
        body(
            http.perform(
                    payload(
                        post("/internal/auth/introspect").header("X-Iam-Internal-Key", INTERNAL),
                        Map.of("credential", selected, "type", "BEARER")))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resolved.path("tenantId").isNull()).isTrue();
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
    http.perform(authorized(get("/api/v1/auth/me"), owner.token()))
        .andExpect(status().isUnauthorized());
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
    String old = select(member, tenant).path("accessToken").asText();
    http.perform(
            payload(
                authorized(
                    put("/api/v1/tenants/" + tenant + "/members/" + member.id() + "/role"),
                    owner.token()),
                Map.of("roleId", role(tenant, "ACCOUNTING"))))
        .andExpect(status().isNoContent());
    var refreshed =
        body(
            http.perform(authorized(post("/api/v1/auth/refresh"), old))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(refreshed.path("permissions").toString())
        .doesNotContain("order.create")
        .contains("finance.reconciliation");
    http.perform(
            authorized(
                delete("/api/v1/tenants/" + tenant + "/members/" + member.id()), owner.token()))
        .andExpect(status().isNoContent());
    http.perform(authorized(get("/api/v1/tenants/" + tenant), old))
        .andExpect(status().isForbidden());
    assertThat(
            body(http.perform(authorized(post("/api/v1/auth/refresh"), old))
                    .andExpect(status().isOk())
                    .andReturn())
                .path("tenantId")
                .isNull())
        .isTrue();
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
                            "PLATFORM_ADMIN",
                            "permissions",
                            List.of(
                                "tenant.view", "tenant.member.role_assign", "tenant.member.view"))))
                .andExpect(status().isCreated())
                .andReturn());
    accept(member, invite(owner, tenant, member.email(), limited.path("id").asText()));
    String token = select(member, tenant).path("accessToken").asText();
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
  void introspectionRequiresItsInternalSecret() throws Exception {
    Account user = register("user@example.com");
    http.perform(
            payload(
                post("/internal/auth/introspect"),
                Map.of("credential", user.token(), "type", "BEARER")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aValidSignatureCannotAttachAnotherUsersSession() throws Exception {
    Account first = register("first@example.com");
    Account second = register("second@example.com");
    UUID session =
        db.queryForObject(
            "SELECT id FROM sessions WHERE user_id=CAST(? AS uuid)", UUID.class, first.id());
    String mismatched =
        tokens.generateSessionAccessToken(
            UUID.fromString(second.id()),
            null,
            "PLATFORM_ADMIN",
            session,
            List.of(),
            Set.of(),
            java.time.Instant.now().plusSeconds(3600));
    http.perform(authorized(get("/api/v1/auth/me"), mismatched))
        .andExpect(status().isUnauthorized());
    String legacy =
        tokens.generateAccessToken(
            UUID.fromString(first.id()),
            UUID.randomUUID(),
            first.email(),
            "First",
            List.of("OWNER"),
            List.of("tenant.manage"));
    http.perform(authorized(get("/api/v1/auth/me"), legacy)).andExpect(status().isUnauthorized());
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
                    .getBean(com.vishop.iam.contracts.tenant.TenantApi.class)
                    .create(UUID.randomUUID(), "transaction-required", "Shop"))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.vishop.iam.contracts.access.AccessApi.class)
                    .initializeOwner(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.vishop.iam.contracts.tenant.TenantApi.class)
                    .lockTenant(UUID.randomUUID()))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void tenantCreationRollsBackJpaJdbcAndDefaultRolesWhenOutboxFails() throws Exception {
    Account owner = register("rollback@example.com");
    var user =
        context.getBean(UserRepositoryPort.class).user(UUID.fromString(owner.id())).orElseThrow();
    var actor =
        new IamActor(
            user.id(), UUID.randomUUID(), null, user.systemRole().name(), List.of(), Set.of());
    var events = org.mockito.Mockito.mock(IamEventPublisherPort.class);
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
    var handler =
        new CreateTenantCommandHandler(
            context.getBean(com.vishop.iam.contracts.identity.IdentityApi.class),
            new com.vishop.iam.modules.tenant.application.api.TenantApplicationApi(
                context.getBean(TenantRepositoryPort.class), events),
            context.getBean(com.vishop.iam.contracts.access.AccessApi.class));
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
    var actor =
        new IamActor(
            user.id(), UUID.randomUUID(), null, user.systemRole().name(), List.of(), Set.of());
    var access = context.getBean(com.vishop.iam.contracts.access.AccessApi.class);
    var failingAccess = org.mockito.Mockito.mock(com.vishop.iam.contracts.access.AccessApi.class);
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
            context.getBean(com.vishop.iam.contracts.identity.IdentityApi.class),
            context.getBean(com.vishop.iam.contracts.tenant.TenantApi.class),
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
                "SELECT count(*) FROM outbox_events WHERE status='PUBLISHED' AND published_at IS NOT NULL",
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
                "SELECT retry_count FROM outbox_events WHERE status='PENDING'", Integer.class))
        .isEqualTo(1);
    for (int i = 0; i < 4; i++) tx.executeWithoutResult(status -> relay.publish());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE status='FAILED' AND published_at IS NULL",
                Integer.class))
        .isEqualTo(1);
  }
}
