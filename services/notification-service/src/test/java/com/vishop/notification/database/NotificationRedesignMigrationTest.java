package com.vishop.notification.database;

import static org.assertj.core.api.Assertions.*;

import io.github.coreplatform.common.domain.UuidV7;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

class NotificationRedesignMigrationTest {
  static EmbeddedPostgres postgres;
  static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID NOTIFICATION = UUID.fromString("00000000-0000-0000-0000-000000000002");
  static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000003");
  static final UUID EMAIL = UUID.fromString("00000000-0000-0000-0000-000000000004");
  static final UUID TELEGRAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
  static final UUID LARK = UUID.fromString("00000000-0000-0000-0000-000000000006");
  Connection db;
  String url;

  @BeforeAll
  static void start() throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
  }

  @AfterAll
  static void stop() throws Exception {
    if (postgres != null) postgres.close();
  }

  @BeforeEach
  void legacyDatabase() throws Exception {
    String name = "redesign_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    url = postgres.getJdbcUrl("postgres", name);
    Flyway.configure().dataSource(url, "postgres", "postgres").target("15").load().migrate();
    db = DriverManager.getConnection(url, "postgres", "postgres");
    execute(
        "INSERT INTO notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,required_permission,severity,title,body,metadata,occurred_at) VALUES (?,?, 'old-event','CUSTOM_LEGACY_TYPE','SKU','sku-1','inventory.view','WARNING','Original title','Original body','{\"sku\":\"old-sku\"}','2026-01-01T00:00:00Z')",
        NOTIFICATION,
        TENANT);
    execute(
        "INSERT INTO notification_recipients(id,tenant_id,notification_id,user_id,created_at,read_at,deleted_at,version) VALUES (?,?,?,?,'2026-01-01','2026-01-02','2026-01-03',7)",
        UuidV7.nextId(),
        TENANT,
        NOTIFICATION,
        USER);
    for (var setting : Map.of(EMAIL, "EMAIL", TELEGRAM, "TELEGRAM", LARK, "LARK").entrySet()) {
      execute(
          "INSERT INTO notification_settings(id,tenant_id,channel,name,enabled,connection_config,version) VALUES (?,?,?,'Existing connection',false,'{\"connectionRef\":\"provider/old\"}',4)",
          setting.getKey(),
          TENANT,
          setting.getValue());
    }
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,in_app_enabled,email_enabled,telegram_enabled,lark_enabled,thresholds,cooldown_seconds,version) VALUES (?,?,'CUSTOM_LEGACY_TYPE',true,true,true,true,'{\"amount\":17}',172800,9)",
        UuidV7.nextId(),
        TENANT);
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,in_app_enabled,email_enabled,telegram_enabled,lark_enabled,enabled,deleted_at) VALUES (?,?,'STOCK_LOW',false,false,false,false,false,now())",
        UuidV7.nextId(),
        TENANT);
    UUID sent = UuidV7.nextId();
    execute(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,user_id,destination,template_code,template_version,template_data) VALUES (?,?,?,?,'Pending@example.test','OLD_TEMPLATE',3,'{\"keep\":true}')",
        UuidV7.nextId(),
        TENANT,
        NOTIFICATION,
        USER);
    execute(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,user_id,destination,template_code,status,attempt_count,sent_at,provider_message_id) VALUES (?,?,?,?,'Sent@example.test','OLD_SENT','SENT',1,now(),'old-provider-id')",
        sent,
        TENANT,
        NOTIFICATION,
        USER);
    execute(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,destination,template_code,status,attempt_count,lock_token,locked_until) VALUES (?,?,?,'Processing@example.test','OLD_PROCESSING','PROCESSING',1,?,now()+interval '1 hour')",
        UuidV7.nextId(),
        TENANT,
        NOTIFICATION,
        UUID.randomUUID());
    execute(
        "INSERT INTO notification_delivery_attempts(id,tenant_id,delivery_id,attempt_number,status,started_at,finished_at,provider_message_id) VALUES (?,?,?,1,'SENT',now(),now(),'old-provider-id')",
        UuidV7.nextId(),
        TENANT,
        sent);
    for (String channel : List.of("IN_APP", "EMAIL")) {
      execute(
          "INSERT INTO notification_deduplication(tenant_id,type,subject_type,subject_id,user_id,channel,last_notified_at,last_severity) VALUES (?,'CUSTOM_LEGACY_TYPE','SKU','sku-1',?,?,'2026-01-01T00:00:00Z','WARNING')",
          TENANT,
          USER,
          channel);
    }
    execute(
        "INSERT INTO consumed_messages VALUES ('old-event','notification-consumer','StockLow',now())");
    execute(
        "INSERT INTO outbox_events(id,tenant_id,event_type,aggregate_type,aggregate_id,payload,occurred_at) VALUES (?,?,'NotificationCreated','Notification',?,'{\"keep\":true}',now())",
        UuidV7.nextId(),
        TENANT,
        NOTIFICATION);
  }

  @AfterEach
  void close() throws Exception {
    if (db != null) db.close();
  }

  @Test
  void upgradePreservesEveryBusinessAndOperationalRowAndCanRunAgain() throws Exception {
    var before = snapshots();
    var flyway = Flyway.configure().dataSource(url, "postgres", "postgres").target("16").load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    flyway.validate();
    assertThat(snapshots()).isEqualTo(before);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE type='CUSTOM_LEGACY_TYPE' AND channels=ARRAY['IN_APP','EMAIL','TELEGRAM','LARK']"))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE type='STOCK_LOW' AND cardinality(channels)=0 AND deleted_at IS NOT NULL"))
        .isEqualTo(1);
    try (var statement = db.createStatement();
        var rows =
            statement.executeQuery("SELECT id,recipient_key FROM notification_deduplication")) {
      int count = 0;
      while (rows.next()) {
        UUID id = rows.getObject("id", UUID.class);
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.getMostSignificantBits() >>> 16)
            .isEqualTo(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
        assertThat(rows.getString("recipient_key")).isEqualTo("USER:" + USER);
        count++;
      }
      assertThat(count).isEqualTo(2);
    }
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void externalDeliveriesAllowGroupsAndEnforceTenantChannelAndDestinationIdentity()
      throws Exception {
    migrate();
    delivery("TELEGRAM", "-100123", null, TELEGRAM);
    rejects("23505", () -> delivery("TELEGRAM", " -100123 ", USER, TELEGRAM));
    delivery("LARK", "https://example.test/Webhook", null, LARK);
    delivery("LARK", "https://example.test/webhook", USER, LARK);
    delivery("LARK", "https://example.test/" + "x".repeat(2000), null, LARK);
    rejects("23505", () -> delivery("EMAIL", " pending@EXAMPLE.test ", null, EMAIL));
    rejects("23503", () -> delivery("TELEGRAM", "-100456", null, EMAIL));
    UUID otherTenantSetting = UuidV7.nextId();
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'TELEGRAM')",
        otherTenantSetting,
        UUID.randomUUID());
    rejects("23503", () -> delivery("TELEGRAM", "-100456", null, otherTenantSetting));
    rejects("23514", () -> delivery("IN_APP", USER.toString(), USER, null));
    rejects("23514", () -> delivery("LARK", " ", null, null));
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_deliveries WHERE template_code='GENERIC_NOTIFICATION' AND user_id IS NULL"))
        .isEqualTo(3);
  }

  @Test
  void cooldownRoutesSupportGroupsWithoutUsersAndPreserveInAppRequirements() throws Exception {
    migrate();
    cooldown("TELEGRAM", "-100123", null, TELEGRAM);
    rejects("23505", () -> cooldown("TELEGRAM", " -100123 ", USER, TELEGRAM));
    cooldown("TELEGRAM", "-100456", USER, TELEGRAM);
    cooldown("LARK", "https://example.test/Webhook", null, LARK);
    cooldown("LARK", "https://example.test/webhook", null, LARK);
    cooldown("EMAIL", "Member@example.test", null, EMAIL);
    rejects("23505", () -> cooldown("EMAIL", " member@EXAMPLE.test ", USER, EMAIL));
    rejects("23502", () -> cooldown("IN_APP", null, null, null));
    rejects("23514", () -> cooldown("IN_APP", "group", USER, null));
    rejects("23502", () -> cooldown("TELEGRAM", null, null, TELEGRAM));
    rejects("23503", () -> cooldown("LARK", "other-webhook", null, TELEGRAM));
    UUID foreignSetting = UuidV7.nextId();
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'LARK')",
        foreignSetting,
        UUID.randomUUID());
    rejects("23503", () -> cooldown("LARK", "foreign-webhook", null, foreignSetting));
    rejects("23514", () -> execute("UPDATE notification_alert_rules SET channels=ARRAY['SMS']"));
    rejects(
        "23514",
        () -> execute("UPDATE notification_alert_rules SET channels=ARRAY['EMAIL','EMAIL']"));
    rejects(
        "23514", () -> execute("UPDATE notification_alert_rules SET channels=ARRAY['EMAIL',NULL]"));
    rejects(
        "23514",
        () -> execute("UPDATE notification_alert_rules SET channels=ARRAY[['EMAIL'],['LARK']]"));
  }

  private void migrate() {
    Flyway.configure().dataSource(url, "postgres", "postgres").target("16").load().migrate();
  }

  @Test
  void retiringCooldownArchivesEveryExistingRowWithoutChangingOtherData() throws Exception {
    migrate();
    cooldown("TELEGRAM", "-100123", null, TELEGRAM);
    var before = snapshots();
    String fullRows;
    try (var statement = db.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM notification_deduplication t")) {
      rows.next();
      fullRows = rows.getString(1);
    }
    var latest = Flyway.configure().dataSource(url, "postgres", "postgres").target("17").load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='notification_deduplication'"))
        .isZero();
    try (var statement = db.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM notification_deduplication_archive t")) {
      rows.next();
      assertThat(rows.getString(1)).isEqualTo(fullRows);
    }
    assertThat(snapshots()).isEqualTo(before);
    assertThat(latest.migrate().migrationsExecuted).isZero();
  }

  private Map<String, String> snapshots() throws Exception {
    var excluded =
        Map.of(
            "notification_alert_rules",
                "in_app_enabled,email_enabled,telegram_enabled,lark_enabled,channels",
            "notification_deliveries", "setting_id,destination_key",
            "notification_deduplication", "id,destination,setting_id,recipient_key");
    var result = new TreeMap<String, String>();
    for (String table :
        List.of(
            "notifications",
            "notification_recipients",
            "notification_settings",
            "notification_alert_rules",
            "notification_deliveries",
            "notification_delivery_attempts",
            "notification_deduplication",
            "consumed_messages",
            "outbox_events")) {
      String physicalTable = table;
      if (table.equals("notification_deduplication")
          && tableExists("notification_deduplication_archive"))
        physicalTable = "notification_deduplication_archive";
      if (!tableExists(physicalTable)) continue;
      String fields = excluded.getOrDefault(table, "");
      String keys =
          fields.isEmpty() ? "ARRAY[]::text[]" : "ARRAY['" + fields.replace(",", "','") + "']";
      try (var statement = db.createStatement();
          var rows =
              statement.executeQuery(
                  "SELECT coalesce(jsonb_agg(value ORDER BY value::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t)-"
                      + keys
                      + " AS value FROM "
                      + physicalTable
                      + " t) s")) {
        rows.next();
        result.put(table, rows.getString(1));
      }
    }
    return result;
  }

  @Test
  void cleanupDropsOnlyUnusedHistoryAndPreservesAllOperationalData() throws Exception {
    migrate();
    Flyway.configure().dataSource(url, "postgres", "postgres").target("17").load().migrate();
    var before = snapshots();
    before.remove("notification_deduplication");
    before.remove("notification_delivery_attempts");
    var latest = Flyway.configure().dataSource(url, "postgres", "postgres").target("18").load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(tableExists("notification_deduplication_archive")).isFalse();
    assertThat(tableExists("notification_delivery_attempts")).isFalse();
    assertThat(snapshots()).isEqualTo(before).hasSize(7);
    assertThat(latest.migrate().migrationsExecuted).isZero();
  }

  private boolean tableExists(String table) throws SQLException {
    return scalar(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='"
                + table
                + "'")
        == 1;
  }

  @Test
  void multipleLarkMigrationKeepsRowsAndTenantChannelDeliveryForeignKey() throws Exception {
    migrate();
    Flyway.configure().dataSource(url, "postgres", "postgres").target("18").load().migrate();
    var before = snapshots();
    var latest = Flyway.configure().dataSource(url, "postgres", "postgres").load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(snapshots()).isEqualTo(before);
    UUID group = UuidV7.nextId();
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel,name) VALUES (?,?,'LARK','Second group')",
        group,
        TENANT);
    assertThat(scalar("SELECT count(*) FROM notification_settings WHERE channel='LARK'"))
        .isEqualTo(2);
    rejects(
        "23505",
        () ->
            execute(
                "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
                UuidV7.nextId(),
                TENANT));
    delivery("LARK", "https://example.test/second-group", null, group);
    rejects("23503", () -> delivery("EMAIL", "wrong-channel@example.test", null, group));
    assertThat(latest.migrate().migrationsExecuted).isZero();
  }

  private void delivery(String channel, String destination, UUID user, UUID setting)
      throws SQLException {
    execute(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,user_id,channel,destination,setting_id) VALUES (?,?,?,?,?,?,?)",
        UuidV7.nextId(),
        TENANT,
        NOTIFICATION,
        user,
        channel,
        destination,
        setting);
  }

  private void cooldown(String channel, String destination, UUID user, UUID setting)
      throws SQLException {
    execute(
        "INSERT INTO notification_deduplication(id,tenant_id,type,subject_type,subject_id,user_id,channel,destination,setting_id,last_notified_at,last_severity) VALUES (?,?,'STOCK_LOW','SKU','sku-2',?,?,?,?,now(),'WARNING')",
        UuidV7.nextId(),
        TENANT,
        user,
        channel,
        destination,
        setting);
  }

  private void execute(String sql, Object... parameters) throws SQLException {
    try (var statement = db.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
      statement.executeUpdate();
    }
  }

  private long scalar(String sql) throws SQLException {
    try (var statement = db.createStatement();
        var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private void rejects(String state, SqlAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo(state));
  }

  @FunctionalInterface
  interface SqlAction {
    void run() throws SQLException;
  }
}
