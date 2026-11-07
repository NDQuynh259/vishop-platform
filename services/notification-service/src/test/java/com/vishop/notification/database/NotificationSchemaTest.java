package com.vishop.notification.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coreplatform.common.domain.UuidV7;
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
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(12);
    db = DriverManager.getConnection(url, "postgres", "postgres");
    execute(
        "INSERT INTO"
            + " notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,required_permission,severity,title,body,occurred_at)"
            + " VALUES (?,?,?,'STOCK_LOW','SKU','sku-1','inventory.view','WARNING','Low"
            + " stock','Check stock',now())",
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
  void catalogTablesAreRemovedWhileLocalDataRelationshipsRemain() throws Exception {
    assertThat(
            scalar(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND"
                    + " table_name IN ('notification_features','notification_types')"))
        .isZero();
    UUID user = UUID.randomUUID();
    recipient(tenant, user);
    rejects("23503", () -> recipient(UUID.randomUUID(), user));
    assertThat(scalar("SELECT count(*) FROM pg_proc WHERE proname='notification_catalog_uuid_v7'"))
        .isZero();
  }

  @Test
  void commonColumnsDefaultCorrectlyAndDeletedSettingsCanBeRestoredWithoutDuplicates()
      throws Exception {
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
        UuidV7.nextId(),
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND created_at IS NOT"
                    + " NULL AND updated_at=created_at AND deleted_at IS NULL AND version=0",
                tenant))
        .isEqualTo(1);
    execute(
        "UPDATE notification_settings SET"
            + " deleted_at=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE"
            + " tenant_id=? AND channel='EMAIL' AND version=0",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND deleted_at IS"
                    + " NULL",
                tenant))
        .isZero();
    rejects(
        "23505",
        () ->
            execute(
                "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
                UuidV7.nextId(),
                tenant));
    execute(
        "UPDATE notification_settings SET"
            + " deleted_at=NULL,updated_at=clock_timestamp(),version=version+1 WHERE tenant_id=?"
            + " AND channel='EMAIL' AND version=1",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND deleted_at IS"
                    + " NULL AND version=2",
                tenant))
        .isEqualTo(1);
    rejects("23514", () -> execute("UPDATE notifications SET version=-1"));
    rejects("23502", () -> execute("UPDATE notification_settings SET updated_at=NULL"));
  }

  @Test
  void personalSettingsAreUniqueAndIndependentByTenantUserAndType() throws Exception {
    UUID user = UUID.randomUUID();
    setting(tenant, user, "STOCK_LOW");
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_user_preferences WHERE enabled AND"
                    + " in_app_enabled AND email_enabled AND version=0"))
        .isEqualTo(1);
    rejects("23505", () -> setting(tenant, user, "STOCK_LOW"));
    setting(UUID.randomUUID(), user, "STOCK_LOW");
    setting(tenant, UUID.randomUUID(), "STOCK_LOW");
    setting(tenant, user, "SYNC_FAILED");
    execute(
        "UPDATE notification_user_preferences SET"
            + " email_enabled=false,updated_at=clock_timestamp(),version=version+1 WHERE"
            + " tenant_id=? AND user_id=? AND type='STOCK_LOW'",
        tenant,
        user);
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE email_enabled"))
        .isEqualTo(3);
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE in_app_enabled"))
        .isEqualTo(4);
    rejects("23514", () -> setting(tenant, user, " "));
    rejects("23514", () -> execute("UPDATE notification_user_preferences SET version=-1"));
  }

  @Test
  void nameMigrationBackfillsExistingRecordsAndEnforcesConstraints() throws Exception {
    String database = "setting_name_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate("CREATE DATABASE " + database);
    }
    String url = postgres.getJdbcUrl("postgres", database);
    Flyway.configure().dataSource(url, "postgres", "postgres").target("11").load().migrate();
    UUID id = UuidV7.nextId();
    UUID tenant = UUID.randomUUID();
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var insert =
            connection.prepareStatement(
                "INSERT INTO notification_settings(id,tenant_id,channel,connection_config) VALUES"
                    + " (?,?,'EMAIL','{\"fromAddress\":\"sender@example.com\"}')")) {
      insert.setObject(1, id);
      insert.setObject(2, tenant);
      insert.executeUpdate();
    }
    assertThat(
            Flyway.configure()
                .dataSource(url, "postgres", "postgres")
                .load()
                .migrate()
                .migrationsExecuted)
        .isEqualTo(1);
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT id,tenant_id,name,connection_config->>'fromAddress' AS email FROM"
                    + " notification_settings")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getObject("id", UUID.class)).isEqualTo(id);
      assertThat(result.getObject("tenant_id", UUID.class)).isEqualTo(tenant);
      assertThat(result.getString("name")).isEqualTo("EMAIL");
      assertThat(result.getString("email")).isEqualTo("sender@example.com");
    }
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var statement = connection.createStatement()) {
      assertThatThrownBy(() -> statement.executeUpdate("UPDATE notification_settings SET name=' '"))
          .isInstanceOf(java.sql.SQLException.class);
      assertThatThrownBy(
              () -> statement.executeUpdate("UPDATE notification_settings SET name=NULL"))
          .isInstanceOf(java.sql.SQLException.class);
      assertThatThrownBy(
              () ->
                  statement.executeUpdate("UPDATE notification_settings SET name=repeat('x',151)"))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
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
          "INSERT INTO"
              + " notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,required_permission,severity,title,body,occurred_at)"
              + " VALUES"
              + " ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','event-1','STOCK_LOW','SKU','sku-1','inventory.view','WARNING','Low"
              + " stock','Check stock',now())");
      statement.executeUpdate("UPDATE notifications SET created_at='2026-01-01 00:00:00Z'");
      if (previousVersion >= 5)
        statement.executeUpdate("UPDATE notifications SET updated_at=created_at");
      if (previousVersion >= 3) {
        if (previousVersion == 6) {
          try (var insert =
              connection.prepareStatement(
                  "INSERT INTO notification_types(id,code,feature_code,category,name) VALUES"
                      + " (?,'CUSTOM_LEGACY_TYPE','LEGACY','BUSINESS','Custom')")) {
            insert.setObject(1, UuidV7.nextId());
            insert.executeUpdate();
          }
        } else if (previousVersion < 6)
          statement.executeUpdate(
              "INSERT INTO notification_types(code,feature_code,category,name) VALUES"
                  + " ('CUSTOM_LEGACY_TYPE','LEGACY','BUSINESS','Custom')");
        if (previousVersion >= 8) {
          try (var insert =
              connection.prepareStatement(
                  "INSERT INTO notification_tenant_features(id,tenant_id,feature_code,enabled)"
                      + " VALUES (?,'00000000-0000-0000-0000-000000000002','INVENTORY',false)")) {
            insert.setObject(1, UuidV7.nextId());
            insert.executeUpdate();
          }
        } else
          statement.executeUpdate(
              "INSERT INTO notification_tenant_features(tenant_id,feature_code,enabled) VALUES"
                  + " ('00000000-0000-0000-0000-000000000002','INVENTORY',false)");
        String channelTable =
            previousVersion >= 4 ? "notification_settings" : "notification_tenant_channels";
        statement.executeUpdate(
            "INSERT INTO "
                + channelTable
                + "(tenant_id,channel,enabled,connection_ref) VALUES"
                + " ('00000000-0000-0000-0000-000000000002','EMAIL',true,'existing-email-provider')");
      }
      statement.executeUpdate(
          "INSERT INTO notification_alert_rules(id,tenant_id,type,email_enabled) VALUES"
              + " ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','CUSTOM_LEGACY_TYPE',true)");
      if (previousVersion >= 2) {
        String preferencesTable =
            previousVersion >= 4 ? "notification_user_preferences" : "notification_settings";
        statement.executeUpdate(
            "INSERT INTO "
                + preferencesTable
                + "(id,tenant_id,user_id,type,email_enabled) VALUES"
                + " ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000005','STOCK_LOW',false)");
      }
      var latest = Flyway.configure().dataSource(url, "postgres", "postgres").load();
      assertThat(latest.migrate().migrationsExecuted).isEqualTo(12 - previousVersion);
      latest.validate();
      if (previousVersion >= 3) {
        try (var result =
            statement.executeQuery(
                "SELECT id,enabled,created_at,version FROM notification_tenant_features WHERE"
                    + " tenant_id='00000000-0000-0000-0000-000000000002' AND"
                    + " feature_code='INVENTORY'")) {
          assertThat(result.next()).isTrue();
          UUID id = result.getObject("id", UUID.class);
          assertThat(id.version()).isEqualTo(7);
          assertThat(id.variant()).isEqualTo(2);
          if (previousVersion < 8)
            assertThat(id.getMostSignificantBits() >>> 16)
                .isEqualTo(result.getTimestamp("created_at").toInstant().toEpochMilli());
          assertThat(result.getBoolean("enabled")).isFalse();
          assertThat(result.getLong("version")).isZero();
        }
      }
      try (var result =
          statement.executeQuery(
              "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND"
                  + " table_name IN ('notification_features','notification_types')")) {
        result.next();
        assertThat(result.getLong(1)).isZero();
      }
      try (var result =
          statement.executeQuery(
              "SELECT updated_at=created_at AND created_at='2026-01-01 00:00:00Z'::timestamptz AND"
                  + " deleted_at IS NULL AND version=0 FROM notifications")) {
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
              "SELECT type FROM notification_alert_rules WHERE type='CUSTOM_LEGACY_TYPE'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("CUSTOM_LEGACY_TYPE");
      }
      try (var result =
          statement.executeQuery(
              "SELECT enabled,id,created_at,connection_config->>'connectionRef' AS connection_ref"
                  + " FROM notification_settings WHERE channel='EMAIL'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isTrue();
        var id = result.getObject("id", UUID.class);
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16)
            .isEqualTo(result.getTimestamp("created_at").toInstant().toEpochMilli());
        if (previousVersion >= 3)
          assertThat(result.getString("connection_ref")).isEqualTo("existing-email-provider");
      }
    }
  }

  @Test
  void jsonMigrationPreservesAllConnectionFieldsAndAudit() throws Exception {
    String name = "json_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    String url = postgres.getJdbcUrl("postgres", name);
    Flyway.configure().dataSource(url, "postgres", "postgres").target("9").load().migrate();
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO"
              + " notification_settings(id,tenant_id,channel,enabled,connection_ref,credentials_ref,telegram_bot_username,telegram_chat_id,telegram_message_thread_id,deleted_at,version)"
              + " VALUES"
              + " ('00000000-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000002','TELEGRAM',true,'provider/1','secrets/1','test_bot','-100123',12,now(),7)");
      Flyway.configure().dataSource(url, "postgres", "postgres").load().migrate();
      try (var result =
          statement.executeQuery(
              "SELECT"
                  + " connection_config->>'connectionRef',connection_config->>'credentialsRef',connection_config->>'telegramBotUsername',connection_config->>'telegramChatId',connection_config->>'telegramMessageThreadId',enabled,deleted_at"
                  + " IS NOT NULL,version,updated_at=created_at FROM notification_settings")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("provider/1");
        assertThat(result.getString(2)).isEqualTo("secrets/1");
        assertThat(result.getString(3)).isEqualTo("test_bot");
        assertThat(result.getString(4)).isEqualTo("-100123");
        assertThat(result.getLong(5)).isEqualTo(12);
        assertThat(result.getBoolean(6)).isTrue();
        assertThat(result.getBoolean(7)).isTrue();
        assertThat(result.getLong(8)).isEqualTo(7);
        assertThat(result.getBoolean(9)).isTrue();
      }
      try (var result =
          statement.executeQuery(
              "SELECT count(*) FROM information_schema.columns WHERE"
                  + " table_name='notification_settings' AND column_name IN"
                  + " ('connection_ref','credentials_ref','telegram_bot_username','telegram_chat_id','telegram_message_thread_id')")) {
        result.next();
        assertThat(result.getLong(1)).isZero();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "null",
        "{\"connectionRef\":true}",
        "{\"credentialsRef\":\" \"}",
        "{\"telegramMessageThreadId\":1.5}",
        "{\"telegramMessageThreadId\":9223372036854775808}"
      })
  void rejectsMalformedConnectionJson(String config) throws Exception {
    rejects(
        "23514",
        () ->
            execute(
                "INSERT INTO notification_settings(id,tenant_id,channel,connection_config) VALUES"
                    + " (?,?,'TELEGRAM',?::jsonb)",
                UuidV7.nextId(),
                tenant,
                config));
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
  void legacyCodesRemainReadableButBlankBusinessCodesAreRejected() throws Exception {
    setting(tenant, UUID.randomUUID(), "CUSTOM_LEGACY_TYPE");
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_user_preferences WHERE"
                    + " type='CUSTOM_LEGACY_TYPE'"))
        .isEqualTo(1);
    rejects("23514", () -> setting(tenant, UUID.randomUUID(), " "));
    rejects("23514", () -> execute("UPDATE notifications SET type=' '"));
  }

  @Test
  void telegramConfigurationRequiresBotCredentialsAndDestinationBeforeEnabling() throws Exception {
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'TELEGRAM')",
        UuidV7.nextId(),
        tenant);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET enabled=true WHERE tenant_id=? AND"
                    + " channel='TELEGRAM'",
                tenant));
    execute(
        "UPDATE notification_settings SET"
            + " connection_config='{\"telegramBotUsername\":\"vishop_alert_bot\",\"telegramChatId\":\"-100123456789\",\"telegramMessageThreadId\":12,\"credentialsRef\":\"secrets/tenant-a/telegram-bot\"}'"
            + " WHERE tenant_id=? AND channel='TELEGRAM'",
        tenant);
    execute(
        "UPDATE notification_settings SET enabled=true WHERE tenant_id=? AND channel='TELEGRAM'",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE tenant_id=? AND"
                    + " channel='TELEGRAM' AND enabled",
                tenant))
        .isEqualTo(1);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET connection_config=connection_config -"
                    + " 'credentialsRef' WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET"
                    + " connection_config=jsonb_set(connection_config,'{telegramChatId}','\"\"')"
                    + " WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET"
                    + " connection_config=jsonb_set(connection_config,'{telegramMessageThreadId}','0')"
                    + " WHERE tenant_id=? AND channel='TELEGRAM'",
                tenant));
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel,enabled,connection_config) VALUES"
            + " (?,?,'TELEGRAM',true,'{\"telegramChatId\":\"-100987654321\",\"credentialsRef\":\"secrets/tenant-b/telegram-bot\"}')",
        UuidV7.nextId(),
        UUID.randomUUID());
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_settings WHERE channel='TELEGRAM' AND enabled"))
        .isEqualTo(2);
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
        UuidV7.nextId(),
        tenant);
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET connection_config='{\"telegramChatId\":\"123\"}'"
                    + " WHERE channel='EMAIL'"));
    setting(tenant, UUID.randomUUID(), "STOCK_LOW");
    assertThat(scalar("SELECT count(*) FROM notification_user_preferences WHERE telegram_enabled"))
        .isEqualTo(1);
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,telegram_enabled) VALUES"
            + " (?,?,'STOCK_LOW',true)",
        UUID.randomUUID(),
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND"
                    + " telegram_enabled",
                tenant))
        .isEqualTo(1);
  }

  @Test
  void tenantsChooseFeaturesAndChannelsIndependently() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    execute(
        "INSERT INTO notification_tenant_features(id,tenant_id,feature_code,enabled) VALUES"
            + " (?,?,'INVENTORY',false),(?,?,'INVENTORY',true)",
        UuidV7.nextId(),
        tenant,
        UuidV7.nextId(),
        otherTenant);
    execute(
        "INSERT INTO notification_settings(id,tenant_id,channel,enabled,connection_config) VALUES"
            + " (?,?,'EMAIL',true,'{\"connectionRef\":\"email-connection-1\"}'),(?,?,'EMAIL',false,'{}'),(?,?,'IN_APP',true,'{}')",
        UuidV7.nextId(),
        tenant,
        UuidV7.nextId(),
        otherTenant,
        UuidV7.nextId(),
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
                "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
                UuidV7.nextId(),
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "INSERT INTO notification_tenant_features(id,tenant_id,feature_code) VALUES (?,?,'"
                    + " ')",
                UuidV7.nextId(),
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "INSERT INTO notification_settings(id,tenant_id,channel) VALUES"
                    + " (?,?,'UNSUPPORTED')",
                UuidV7.nextId(),
                tenant));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_settings SET"
                    + " connection_config='{\"connectionRef\":\"secret-reference\"}' WHERE"
                    + " channel='IN_APP'"));
  }

  @Test
  void systemAndBusinessTypesCanBeToggledSeparatelyPerTenant() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    execute(
        "INSERT INTO notification_alert_rules(id,tenant_id,type,enabled) VALUES"
            + " (?,?,'SYSTEM_MAINTENANCE',false),(?,?,'SYSTEM_MAINTENANCE',true),(?,?,'STOCK_LOW',true)",
        UUID.randomUUID(),
        tenant,
        UUID.randomUUID(),
        otherTenant,
        UUID.randomUUID(),
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND"
                    + " type='SYSTEM_MAINTENANCE' AND enabled",
                tenant))
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND"
                    + " type='SYSTEM_MAINTENANCE' AND enabled",
                otherTenant))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_alert_rules WHERE tenant_id=? AND"
                    + " type='STOCK_LOW' AND enabled",
                tenant))
        .isEqualTo(1);
    setting(tenant, UUID.randomUUID(), "SYSTEM_MAINTENANCE");
    execute(
        "UPDATE notification_user_preferences SET in_app_enabled=false WHERE tenant_id=? AND"
            + " type='SYSTEM_MAINTENANCE'",
        tenant);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_user_preferences WHERE tenant_id=? AND"
                    + " in_app_enabled",
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
        "UPDATE notification_recipients SET read_at=clock_timestamp() WHERE tenant_id=? AND"
            + " notification_id=? AND user_id=?",
        tenant,
        notification,
        first);
    assertThat(scalar("SELECT count(*) FROM notification_recipients WHERE read_at IS NULL"))
        .isEqualTo(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM notification_recipients WHERE tenant_id=? AND user_id=? AND"
                    + " read_at IS NULL",
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
                "INSERT INTO notification_alert_rule_recipients(tenant_id,rule_id,user_id) VALUES"
                    + " (?,?,?)",
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
        "UPDATE notification_deliveries SET"
            + " status='PROCESSING',attempt_count=1,locked_until=now()+interval '1"
            + " minute',lock_token=? WHERE id=?",
        UUID.randomUUID(),
        delivery);
    rejects(
        "23514",
        () -> execute("UPDATE notification_deliveries SET attempt_count=6 WHERE id=?", delivery));
    rejects(
        "23514",
        () ->
            execute(
                "UPDATE notification_deliveries SET status='SENT',locked_until=NULL,lock_token=NULL"
                    + " WHERE id=?",
                delivery));
    execute(
        "UPDATE notification_deliveries SET"
            + " status='SENT',sent_at=now(),locked_until=NULL,lock_token=NULL WHERE id=?",
        delivery);
    execute(
        "INSERT INTO"
            + " notification_delivery_attempts(id,tenant_id,delivery_id,attempt_number,status,started_at,finished_at)"
            + " VALUES (?,?,?,1,'SENT',now(),now())",
        UUID.randomUUID(),
        tenant,
        delivery);
    rejects(
        "23503",
        () ->
            execute(
                "INSERT INTO"
                    + " notification_delivery_attempts(id,tenant_id,delivery_id,attempt_number,status,started_at,finished_at)"
                    + " VALUES (?,?,?,2,'FAILED',now(),now())",
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
        "INSERT INTO"
            + " outbox_events(id,tenant_id,event_type,aggregate_type,aggregate_id,payload,occurred_at)"
            + " VALUES (?,?,'NotificationCreated','Notification',?,'{}',now())",
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
                "INSERT INTO consumed_messages VALUES"
                    + " ('event-1','notifications','StockLow',now())"));
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
        "INSERT INTO notification_deduplication VALUES"
            + " (?,'STOCK_LOW','SKU','sku-1',?,?,now(),'WARNING')",
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
        "INSERT INTO"
            + " notification_deliveries(id,tenant_id,notification_id,destination,template_code)"
            + " VALUES (?,?,?,?,'invitation')",
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
