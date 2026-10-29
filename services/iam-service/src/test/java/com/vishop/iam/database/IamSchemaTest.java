package com.vishop.iam.database;

import static org.assertj.core.api.Assertions.*;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.constants.CommonPermissions;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

/** Real PostgreSQL constraints and Flyway migrations; no Docker or production DB required. */
class IamSchemaTest {
  private static EmbeddedPostgres postgres;
  private static Flyway flyway;
  private Connection connection;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    flyway =
        Flyway.configure()
            .dataSource(postgres.getPostgresDatabase())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    if (postgres != null) postgres.close();
  }

  @BeforeEach
  void begin() throws Exception {
    connection = postgres.getPostgresDatabase().getConnection();
    connection.setAutoCommit(false);
  }

  @AfterEach
  void rollback() throws Exception {
    if (connection != null) {
      connection.rollback();
      connection.close();
    }
  }

  private void execute(String sql, Object... values) throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
      statement.execute();
    }
  }

  private String scalar(String sql, Object... values) throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }
  }

  private UUID user(String email) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "INSERT INTO users(id,email,password_hash,full_name) VALUES (?,?,'hash','Test User')",
        id,
        email);
    return id;
  }

  private UUID tenant(String code) throws SQLException {
    UUID id = UUID.randomUUID();
    execute("INSERT INTO tenants(id,code,name) VALUES (?,?,'Test Tenant')", id, code);
    return id;
  }

  private UUID role(UUID tenant, String key) throws SQLException {
    return UUID.fromString(
        scalar("SELECT id FROM roles WHERE tenant_id=? AND builtin_key=?", tenant, key));
  }

  private void member(UUID user, UUID tenant, UUID role) throws SQLException {
    execute(
        "INSERT INTO tenant_members(user_id,tenant_id,role_id) VALUES (?,?,?)", user, tenant, role);
  }

  private void rejects(String state, String sql, Object... values) throws Exception {
    var savepoint = connection.setSavepoint();
    assertThatThrownBy(() -> execute(sql, values))
        .isInstanceOf(SQLException.class)
        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(state));
    connection.rollback(savepoint);
  }

  @Test
  void flywayValidatesAndDoesNotReapplyMigrations() {
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void profileAndSoftDeleteMigrationsPreserveExistingAccountsAndAllowEmailOnlyProfiles()
      throws Exception {
    try (var upgradePostgres = EmbeddedPostgres.builder().setPort(0).start()) {
      var database = upgradePostgres.getPostgresDatabase();
      var previous =
          Flyway.configure()
              .dataSource(database)
              .locations("classpath:db/migration")
              .target("5")
              .load();
      assertThat(previous.migrate().migrationsExecuted).isEqualTo(5);
      try (var upgradeConnection = database.getConnection();
          var statement = upgradeConnection.createStatement()) {
        statement.execute(
            "INSERT INTO users(email,password_hash,full_name) VALUES ('upgrade@example.com','hash','Existing User')");
        statement.execute(
            "INSERT INTO tenants(code,name) VALUES ('upgrade-tenant','Existing Tenant')");
        statement.execute(
            "UPDATE users SET last_tenant_id=(SELECT id FROM tenants WHERE code='upgrade-tenant')");
      }

      var upgraded =
          Flyway.configure().dataSource(database).locations("classpath:db/migration").load();
      assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(3);
      upgraded.validate();
      try (var upgradeConnection = database.getConnection();
          var statement = upgradeConnection.createStatement()) {
        try (var rows =
            statement.executeQuery(
                """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema='public' AND column_name='deleted_at'
              AND table_name IN ('users','tenants','tenant_members','roles','tenant_invitations')
              AND is_nullable='YES' AND data_type='timestamp with time zone'
            """)) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isEqualTo(5);
        }
        try (var rows =
            statement.executeQuery(
                """
            SELECT u.deleted_at IS NULL AND t.deleted_at IS NULL
              AND u.last_tenant_id=t.id AND u.version=0 AND t.version=0
              AND u.full_name='Existing User'
            FROM users u JOIN tenants t ON t.code='upgrade-tenant'
            WHERE u.email='upgrade@example.com'
            """)) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getBoolean(1)).isTrue();
        }
        try (var rows =
            statement.executeQuery(
                "INSERT INTO users(email,password_hash) VALUES ('email-only@example.com','hash') RETURNING full_name")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isNull();
        }
      }
    }
  }

  @Test
  void identityIsGlobalAndRolesAreIndependent() throws Exception {
    UUID user = user("global@example.com");
    UUID first = tenant("first-tenant");
    UUID second = tenant("second-tenant");
    member(user, first, role(first, "OWNER"));
    member(user, second, role(second, "ACCOUNTING"));
    assertThat(scalar("SELECT count(*) FROM tenant_members WHERE user_id=?", user)).isEqualTo("2");
    assertThat(scalar("SELECT system_role FROM users WHERE id=?", user))
        .isEqualTo(SystemRole.USER.name());
    execute("UPDATE users SET system_role='PLATFORM_ADMIN' WHERE id=?", user);
    assertThat(
            scalar(
                "SELECT r.builtin_key FROM tenant_members m JOIN roles r ON r.id=m.role_id "
                    + "WHERE m.user_id=? AND m.tenant_id=?",
                user,
                second))
        .isEqualTo("ACCOUNTING");
    assertThat(
            scalar(
                "SELECT count(*) FROM information_schema.columns WHERE table_name='users' "
                    + "AND column_name IN ('tenant_id','role')"))
        .isEqualTo("0");
  }

  @Test
  void duplicateMembershipAndCrossTenantRoleAreRejected() throws Exception {
    UUID user = user("member@example.com");
    UUID first = tenant("first-tenant");
    UUID second = tenant("second-tenant");
    member(user, first, role(first, "OWNER"));
    rejects(
        "23505",
        "INSERT INTO tenant_members(user_id,tenant_id,role_id) VALUES (?,?,?)",
        user,
        first,
        role(first, "ACCOUNTING"));
    rejects(
        "23503",
        "INSERT INTO tenant_members(user_id,tenant_id,role_id) VALUES (?,?,?)",
        user,
        second,
        role(first, "OWNER"));
    rejects(
        "23503",
        "INSERT INTO tenant_members(user_id,tenant_id,role_id) VALUES (?,?,?)",
        UUID.randomUUID(),
        second,
        role(second, "OWNER"));
  }

  @Test
  void emailUniquenessIsCaseInsensitive() throws Exception {
    user("User@example.com");
    rejects(
        "23505",
        "INSERT INTO users(email,password_hash,full_name) VALUES ('user@example.com','hash','User')");
  }

  @Test
  void systemRolesAndTenantPermissionsCannotBeMixed() throws Exception {
    rejects(
        "23514",
        "INSERT INTO users(email,password_hash,full_name,system_role) "
            + "VALUES ('user@example.com','hash','User','OWNER')");
    UUID tenant = tenant("test-tenant");
    rejects(
        "23514",
        "INSERT INTO role_permissions(role_id,permission_key) VALUES (?,'platform.tenant.manage')",
        role(tenant, "OPERATIONS"));
    rejects(
        "23514",
        "INSERT INTO role_permissions(role_id,permission_key) VALUES (?,'unknown.action')",
        role(tenant, "OPERATIONS"));
  }

  @Test
  void everyTenantGetsFourRolesAndOwnerGetsTheActualPermissionCatalogue() throws Exception {
    UUID tenant = tenant("test-tenant");
    assertThat(scalar("SELECT count(*) FROM roles WHERE tenant_id=?", tenant)).isEqualTo("4");
    Set<String> expected = new HashSet<>();
    for (Class<?> group : CommonPermissions.class.getDeclaredClasses()) {
      for (var field : group.getDeclaredFields()) {
        if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
          expected.add((String) field.get(null));
        }
      }
    }
    Set<String> actual = new HashSet<>();
    try (var statement =
        connection.prepareStatement(
            "SELECT permission_key FROM role_permissions WHERE role_id=?")) {
      statement.setObject(1, role(tenant, "OWNER"));
      try (var result = statement.executeQuery()) {
        while (result.next()) actual.add(result.getString(1));
      }
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    // The check constraint must also accept every code-defined permission on custom roles.
    UUID custom = UUID.randomUUID();
    execute("INSERT INTO roles(id,tenant_id,name) VALUES (?,?,'Custom')", custom, tenant);
    for (String permission : expected) {
      execute(
          "INSERT INTO role_permissions(role_id,permission_key) VALUES (?,?)", custom, permission);
    }
    assertThat(
            scalar(
                "SELECT count(*) FROM role_permissions WHERE role_id=?", role(tenant, "WAREHOUSE")))
        .isEqualTo("10");
    assertThat(
            scalar(
                "SELECT count(*) FROM role_permissions WHERE role_id=? AND permission_key LIKE 'finance.%'",
                role(tenant, "OPERATIONS")))
        .isEqualTo("0");
  }

  @Test
  void roleNamesAndBuiltinKeysAreUniquePerTenant() throws Exception {
    UUID first = tenant("first-tenant");
    UUID second = tenant("second-tenant");
    execute("INSERT INTO roles(tenant_id,name) VALUES (?,'Custom')", first);
    execute("INSERT INTO roles(tenant_id,name) VALUES (?,'Custom')", second);
    rejects("23505", "INSERT INTO roles(tenant_id,name) VALUES (?,'custom')", first);
    rejects(
        "23505",
        "INSERT INTO roles(tenant_id,name,builtin_key) VALUES (?,'Another Owner','OWNER')",
        first);
  }

  @Test
  void invitationsCannotUseForeignTenantRolesOrInviters() throws Exception {
    UUID user = user("inviter@example.com");
    UUID first = tenant("first-tenant");
    UUID second = tenant("second-tenant");
    member(user, first, role(first, "OWNER"));
    String insert =
        "INSERT INTO tenant_invitations(tenant_id,email,role_id,invited_by,token_hash,expires_at) "
            + "VALUES (?,'invitee@example.com',?,?,repeat('a',64),now()+interval '1 day')";
    rejects("23503", insert, first, role(second, "OPERATIONS"), user);
    rejects("23503", insert, second, role(second, "OPERATIONS"), user);
    execute(insert, first, role(first, "OPERATIONS"), user);
    rejects(
        "23505",
        "INSERT INTO tenant_invitations(tenant_id,email,role_id,invited_by,token_hash,expires_at) "
            + "VALUES (?,'INVITEE@example.com',?,?,repeat('b',64),now()+interval '1 day')",
        first,
        role(first, "OPERATIONS"),
        user);
    rejects("23514", "UPDATE tenant_invitations SET status='ACCEPTED'");
    execute("UPDATE tenant_invitations SET status='REVOKED'");
    execute(
        "INSERT INTO tenant_invitations(tenant_id,email,role_id,invited_by,token_hash,expires_at) "
            + "VALUES (?,'invitee@example.com',?,?,repeat('b',64),now()+interval '1 day')",
        first,
        role(first, "OPERATIONS"),
        user);
  }

  @Test
  void tenantCreationAndDefaultRolesRollbackTogether() throws Exception {
    var savepoint = connection.setSavepoint();
    UUID tenant = tenant("rollback-tenant");
    connection.rollback(savepoint);
    assertThat(scalar("SELECT count(*) FROM tenants WHERE id=?", tenant)).isEqualTo("0");
    assertThat(scalar("SELECT count(*) FROM roles WHERE tenant_id=?", tenant)).isEqualTo("0");
  }

  @Test
  void acceptingAnInvitationRequiresMembershipInItsTenant() throws Exception {
    UUID inviter = user("inviter@example.com");
    UUID recipient = user("recipient@example.com");
    UUID tenant = tenant("test-tenant");
    member(inviter, tenant, role(tenant, "OWNER"));
    execute(
        "INSERT INTO tenant_invitations(tenant_id,email,role_id,invited_by,token_hash,expires_at) "
            + "VALUES (?,'recipient@example.com',?,?,repeat('a',64),now()+interval '1 day')",
        tenant,
        role(tenant, "OPERATIONS"),
        inviter);
    String accept =
        "UPDATE tenant_invitations SET status='ACCEPTED',accepted_by=?,accepted_at=now()";
    rejects("23503", accept, recipient);
    member(recipient, tenant, role(tenant, "OPERATIONS"));
    execute(accept, recipient);
    assertThat(scalar("SELECT status FROM tenant_invitations")).isEqualTo("ACCEPTED");
  }

  @Test
  void statelessAuthenticationRemovesSessionAndProjectionStorage() throws Exception {
    assertThat(
            scalar(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('sessions','security_states')"))
        .isEqualTo("0");
    assertThat(
            scalar(
                "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname LIKE 'security_%'"))
        .isEqualTo("0");
  }
}
