package com.vishop.notification.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationSchemaTest {
  static EmbeddedPostgres postgres;
  Connection db;
  Flyway flyway;
  UUID tenant = UUID.randomUUID();
  UUID notification = UUID.randomUUID();

  @BeforeAll
  static void start() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
  }

  @AfterAll
  static void stop() throws Exception {
    if (postgres != null) postgres.close();
  }

  @BeforeEach
  void database() throws Exception {
    String name = "notification_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    String url = postgres.getJdbcUrl("postgres", name);
    flyway = Flyway.configure().dataSource(url, "postgres", "postgres").cleanDisabled(true).load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
    db = DriverManager.getConnection(url, "postgres", "postgres");
    execute(
        "INSERT INTO notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,required_permission,severity,title,body,occurred_at) VALUES (?,?,?,'STOCK_LOW','SKU','sku-1','inventory.view','WARNING','Low stock','Check stock',now())",
        notification,
        tenant,
        UUID.randomUUID().toString());
  }

  @AfterEach
  void close() throws Exception {
    if (db != null) db.close();
  }

  @Test
  void migratesEmptyDatabaseAndCanRunAgain() {
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void commonColumnsDefaultCorrectlyAndDeletedSettingsCanBeRestoredWithoutDuplicates()
      throws Exception {
    execute("INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'EMAIL')", tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND created_at IS NOT NULL AND updated_at=created_at AND deleted_at IS NULL AND version=0",
                tenant))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_features WHERE created_at IS NOT NULL AND updated_at=created_at AND deleted_at IS NULL AND version=0"))
        .isEqualTo(10);
    execute(
        "UPDATE notification_settings SET deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE tenant_id=? AND channel='EMAIL' AND version=0",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND deleted_at IS NULL",
                tenant))
        .isZero();
    rejects(
        "23505",
        () ->
            execute(
                "INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'EMAIL')", tenant));
    execute(
        "UPDATE notification_settings SET deleted_at=NULL,updated_at=clock_timestamp(),version=version+1 WHERE tenant_id=? AND channel='EMAIL' AND version=1",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND deleted_at IS NULL AND version=2",
                tenant))
        .isEqualTo(1);
    rejects("23514", () -> execute("UPDATE notifications SET version=-1"));
    rejects("23502", () -> execute("UPDATE notification_features SET updated_at=NULL"));
  }

  @Test
  void personalSettingsAreUniqueAndIndependentByTenantUserAndType() throws Exception {
    UUID user = UUID.randomUUID();
    setting(tenant, user, "STOCK_LOW");
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_user_preferences WHERE enabled AND in_app_enabled AND email_enabled AND version=0"))
        .isEqualTo(1);
    rejects("23505", () -> setting(tenant, user, "STOCK_LOW"));
    setting(UUID.randomUUID(), user, "STOCK_LOW");
    setting(tenant, UUID.randomUUID(), "STOCK_LOW");
    setting(tenant, user, "SYNC_FAILED");
    execute(
        "UPDATE notification_user_preferences SET email_enabled=false,updated_at=clock_timestamp(),version=version+1 WHERE tenant_id=? AND user_id=? AND type='STOCK_LOW'",
        tenant,
        user);
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE email_enabled"))
        .isEqualTo(3);
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE in_app_enabled"))
        .isEqualTo(4);
    rejects("23514", () -> setting(tenant, user, " "));
    rejects("23514", () -> execute("UPDATE notification_user_preferences SET version=-1"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4})
  void upgradesPreviousVersionsWithoutChangingExistingNotificationData(int previousVersion)
      throws Exception {
    String name = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    String url = postgres.getJdbcUrl("postgres", name);
    var previous =
        Flyway.configure()
            .dataSource(url, "postgres", "postgres")
            .target(String.valueOf(previousVersion))
            .load();
    assertThat(previous.migrate().migrationsExecuted).isEqualTo(previousVersion);
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,required_permission,severity,title,body,occurred_at) VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','event-1','STOCK_LOW','SKU','sku-1','inventory.view','WARNING','Low stock','Check stock',now())");
      statement.executeUpdate("UPDATE notifications SET created_at='2026-01-01 00:00:00Z'");
      if (previousVersion >= 3) {
        statement.executeUpdate(
            "INSERT INTO notification_types(code,feature_code,category,name) VALUES ('CUSTOM_LEGACY_TYPE','LEGACY','BUSINESS','Custom')");
        String channelTable =
            previousVersion >= 4 ? "notification_settings" : "notification_tenant_channels";
        statement.executeUpdate(
            "INSERT INTO "
                + channelTable
                + "(tenant_id,channel,enabled,connection_ref) VALUES ('00000000-0000-0000-0000-000000000002','EMAIL',true,'existing-email-provider')");
      }
      statement.executeUpdate(
          "INSERT INTO notification_alert_rules(id,tenant_id,type,email_enabled) VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','CUSTOM_LEGACY_TYPE',true)");
      if (previousVersion >= 2) {
        String preferencesTable =
            previousVersion >= 4 ? "notification_user_preferences" : "notification_settings";
        statement.executeUpdate(
            "INSERT INTO "
                + preferencesTable
                + "(id,tenant_id,user_id,type,email_enabled) VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000005','STOCK_LOW',false)");
      }
      var latest = Flyway.configure().dataSource(url, "postgres", "postgres").load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(5 - previousVersion);
      latest.validate();
      try (var result =
          statement.executeQuery(
              "SELECT updated_at=created_at AND created_at='2026-01-01 00:00:00Z'::timestamptz AND deleted_at IS NULL AND version=0 FROM notifications")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isTrue();
      }
      try (var result = statement.executeQuery("SELECT title FROM notifications")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("Low stock");
      }
      try (var result =
          statement.executeQuery("SELECT count(*) FROM notification_user_preferences")) {
        result.next();
        assertThat(result.getLong(1)).isEqualTo(previousVersion >= 2 ? 1 : 0);
      }
      try (var result =
          statement.executeQuery(
              "SELECT feature_code FROM notification_types WHERE code='CUSTOM_LEGACY_TYPE'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("LEGACY");
      }
      try (var result =
          statement.executeQuery(
              "SELECT enabled FROM notification_settings WHERE channel='EMAIL'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isTrue();
      }
    }
  }

  private void setting(UUID tenantId, UUID user, String type) throws SQLException {
    execute(
        "INSERT INTO notification_user_preferences(id,tenant_id,user_id,type) VALUES (?,?,?,?)",
        UUID.randomUUID(),
        tenantId,
        user,
        type);
  }

  @Test
  void catalogGroupsSystemAndBusinessTypesAndRejectsUnknownTypes() throws Exception {
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_types WHERE category='SYSTEM' AND feature_code='SYSTEM'"))
        .isEqualTo(3);
    assertThat(scalar("SELECT count(*) FROM notification_types WHERE feature_code='INVENTORY'"))
        .isEqualTo(2);
    rejects("23503", () -> setting(tenant, UUID.randomUUID(), "NOT_REGISTERED"));
    rejects("23503", () -> execute("UPDATE notifications SET type='NOT_REGISTERED'"));
    rejects("23503", () -> execute("UPDATE notification_types SET feature_code='NOT_REGISTERED'"));
  }

  @Test
  void telegramConfigurationRequiresBotCredentialsAndDestinationBeforeEnabling() throws Exception {
    execute("INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'TELEGRAM')", tenant);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET enabled=true WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    execute(
        "UPDATE notification_settings SET telegram_bot_username='vishop_alert_bot',telegram_chat_id='-100123456789',telegram_message_thread_id=12,credentials_ref='secrets/tenant-a/telegram-bot' WHERE tenant_id=? AND channel='TELEGRAM'",
        tenant);
    execute(
        "UPDATE notification_settings SET enabled=true WHERE tenant_id=? AND channel='TELEGRAM'",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND channel='TELEGRAM' AND enabled",
                tenant))
        .isEqualTo(1);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET credentials_ref=NULL WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET telegram_chat_id='' WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET telegram_message_thread_id=0 WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    execute(
        "INSERT INTO notification_settings(tenant_id,channel,enabled,telegram_chat_id,credentials_ref) VALUES (?,'TELEGRAM',true,'-100987654321','secrets/tenant-b/telegram-bot')",
        UUID.randomUUID());
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE channel='TELEGRAM' AND enabled"))
        .isEqualTo(2);
    execute("INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'EMAIL')", tenant);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET telegram_chat_id='123' WHERE channel='EMAIL'"));
    setting(tenant, UUID.randomUUID(), "STOCK_LOW");
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE telegram_enabled"))
        .isEqualTo(1);
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,telegram_enabled) VALUES (?,?,'STOCK_LOW',true)",
        UUID.randomUUID(),
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND telegram_enabled",
                tenant))
        .isEqualTo(1);
  }

  @Test
  void tenantsChooseFeaturesAndChannelsIndependently() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    execute(
        "INSERT INTO notification_tenant_features(tenant_id,feature_code,enabled) VALUES (?,'INVENTORY',false),(?,'INVENTORY',true)",
        tenant,
        otherTenant);
    execute(
        "INSERT INTO notification_settings(tenant_id,channel,enabled,connection_ref) VALUES (?,'EMAIL',true,'email-connection-1'),(?,'EMAIL',false,NULL),(?,'IN_APP',true,NULL)",
        tenant,
        otherTenant,
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_tenant_features WHERE tenant_id=? AND enabled",
                tenant))
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_tenant_features WHERE tenant_id=? AND enabled",
                otherTenant))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND enabled", tenant))
        .isEqualTo(2);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND enabled",
                otherTenant))
        .isZero();
    rejects(
        "23505",
        () ->
            execute(
                "INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'EMAIL')", tenant));
    rejects(
        "23503",
        () ->
            execute(
                "INSERT INTO notification_tenant_features(tenant_id,feature_code) VALUES (?,'NOT_REGISTERED')",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "INSERT INTO notification_settings(tenant_id,channel) VALUES (?,'UNSUPPORTED')",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET connection_ref='secret-reference' WHERE channel='IN_APP'"));
  }

  @Test
  void systemAndBusinessTypesCanBeToggledSeparatelyPerTenant() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,enabled) VALUES (?,?,'SYSTEM_MAINTENANCE',false),(?,?,'SYSTEM_MAINTENANCE',true),(?,?,'STOCK_LOW',true)",
        UUID.randomUUID(),
        tenant,
        UUID.randomUUID(),
        otherTenant,
        UUID.randomUUID(),
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND type='SYSTEM_MAINTENANCE' AND enabled",
                tenant))
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND type='SYSTEM_MAINTENANCE' AND enabled",
                otherTenant))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND type='STOCK_LOW' AND enabled",
                tenant))
        .isEqualTo(1);
    setting(tenant, UUID.randomUUID(), "SYSTEM_MAINTENANCE");
    execute(
        "UPDATE notification_user_preferences SET in_app_enabled=false WHERE tenant_id=? AND type='SYSTEM_MAINTENANCE'",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_user_preferences WHERE tenant_id=? AND in_app_enabled",
                tenant))
        .isZero();
  }

  @Test
  void readStateIsIndependentPerUserAndTenant() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    recipient(tenant, first);
    recipient(tenant, second);
    execute(
        "UPDATE notification_recipients SET read_at=clock_timestamp() WHERE tenant_id=? AND notification_id=? AND user_id=?",
        tenant,
        notification,
        first);
    assertThat(scalar("SELECT count(*) FROM notification_recipients WHERE read_at IS NULL"))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_recipients WHERE tenant_id=? AND user_id=? AND read_at IS NULL",
                tenant,
                first))
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_recipients WHERE tenant_id=? AND user_id=?",
                UUID.randomUUID(),
                second))
        .isZero();
    rejects("23503", () -> recipient(UUID.randomUUID(), first));
    rejects("23505", () -> recipient(tenant, second));
  }

  @Test
  void rulesRejectCrossTenantRecipientsAndInvalidThresholds() throws Exception {
    UUID rule = UUID.randomUUID();
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type) VALUES (?,?,'STOCK_LOW')",
        rule,
        tenant);
    execute(
        "INSERT INTO notification_alert_rule_recipients(tenant_id,rule_id,user_id) VALUES (?,?,?)",
        tenant,
        rule,
        UUID.randomUUID());
    rejects(
        "23503",
        () ->
            execute(
                "INSERT INTO notification_alert_rule_recipients(tenant_id,rule_id,user_id) VALUES (?,?,?)",
                UUID.randomUUID(),
                rule,
                UUID.randomUUID()));
    rejects("23514", () -> execute("UPDATE notification_alert_rules SET thresholds='[]'"));
    rejects("23514", () -> execute("UPDATE notification_alert_rules SET cooldown_seconds=10"));
  }

  @Test
  void deliveriesSupportExternalInviteesAndRejectDuplicateDestinations() throws Exception {
    UUID delivery = delivery(tenant, "invitee@example.com");
    rejects("23505", () -> delivery(tenant, " INVITEE@example.com "));
    rejects("23503", () -> delivery(UUID.randomUUID(), "other@example.com"));
    rejects(
        "23514",
        () ->
            execute("UPDATE notification_deliveries SET status='PROCESSING' WHERE id=?", delivery));
    execute(
        "UPDATE notification_deliveries SET status='PROCESSING',attempt_count=1,locked_until=now()+interval '1 minute',lock_token=? WHERE id=?",
        UUID.randomUUID(),
        delivery);
    rejects(
        "23514",
        () -> execute("UPDATE notification_deliveries SET attempt_count=6 WHERE id=?", delivery));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_deliveries SET status='SENT',locked_until=NULL,lock_token=NULL WHERE id=?",
                delivery));
    execute(
        "UPDATE notification_deliveries SET status='SENT',sent_at=now(),locked_until=NULL,lock_token=NULL WHERE id=?",
        delivery);
    execute(
        "INSERT INTO notification_delivery_attempts(id,tenant_id,delivery_id,attempt_number,status,started_at,finished_at) VALUES (?,?,?,1,'SENT',now(),now())",
        UUID.randomUUID(),
        tenant,
        delivery);
    rejects(
        "23503",
        () ->
            execute(
                "INSERT INTO notification_delivery_attempts(id,tenant_id,delivery_id,attempt_number,status,started_at,finished_at) VALUES (?,?,?,2,'FAILED',now(),now())",
                UUID.randomUUID(),
                UUID.randomUUID(),
                delivery));
  }

  @Test
  void eventProcessingAndRecipientCreationRollBackTogether() throws Exception {
    db.setAutoCommit(false);
    execute("INSERT INTO consumed_messages VALUES ('event-1','notifications','StockLow',now())");
    recipient(tenant, UUID.randomUUID());
    execute(
        "INSERT INTO outbox_events(id,tenant_id,event_type,aggregate_type,aggregate_id,payload,occurred_at) VALUES (?,?,'NotificationCreated','Notification',?,'{}',now())",
        UUID.randomUUID(),
        tenant,
        notification);
    db.rollback();
    db.setAutoCommit(true);
    assertThat(scalar("SELECT count(*) FROM consumed_messages")).isZero();
    assertThat(scalar("SELECT count(*) FROM notification_recipients")).isZero();
    assertThat(scalar("SELECT count(*) FROM outbox_events")).isZero();
    execute("INSERT INTO consumed_messages VALUES ('event-1','notifications','StockLow',now())");
    rejects(
        "23505",
        () ->
            execute(
                "INSERT INTO consumed_messages VALUES ('event-1','notifications','StockLow',now())"));
  }

  @Test
  void deduplicationSeparatesUsersChannelsAndTenants() throws Exception {
    UUID user = UUID.randomUUID();
    dedup(tenant, user, "IN_APP");
    rejects("23505", () -> dedup(tenant, user, "IN_APP"));
    dedup(tenant, user, "EMAIL");
    dedup(tenant, UUID.randomUUID(), "IN_APP");
    dedup(UUID.randomUUID(), user, "IN_APP");
    assertThat(scalar("SELECT count(*) FROM notification_deduplication")).isEqualTo(4);
  }

  private void dedup(UUID tenantId, UUID user, String channel) throws SQLException {
    execute(
        "INSERT INTO notification_deduplication VALUES (?,'STOCK_LOW','SKU','sku-1',?,?,now(),'WARNING')",
        tenantId,
        user,
        channel);
  }

  private void recipient(UUID tenantId, UUID user) throws SQLException {
    execute(
        "INSERT INTO notification_recipients(tenant_id,notification_id,user_id) VALUES (?,?,?)",
        tenantId,
        notification,
        user);
  }

  private UUID delivery(UUID tenantId, String destination) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,destination,template_code) VALUES (?,?,?,?,'invitation')",
        id,
        tenantId,
        notification,
        destination);
    return id;
  }

  private void execute(String sql, Object... args) throws SQLException {
    try (var statement = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      statement.executeUpdate();
    }
  }

  private long scalar(String sql, Object... args) throws SQLException {
    try (var statement = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private void rejects(String sqlState, SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo(sqlState));
  }

  @FunctionalInterface
  interface SqlAction {
    void run() throws SQLException;
  }
}
