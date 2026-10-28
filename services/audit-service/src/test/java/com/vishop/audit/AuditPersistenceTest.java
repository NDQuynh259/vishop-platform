package com.vishop.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.vishop.audit.modules.auditlog.infrastructure.persistence.entities.AuditLogJpaEntity;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories.SpringDataAuditLogRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Flyway creates the PostgreSQL schema; Hibernate validates it and persists JSONB through JPA. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditPersistenceTest {
  private static final EmbeddedPostgres POSTGRES = startPostgres();
  @Autowired SpringDataAuditLogRepository logs;
  @Autowired EntityManager entityManager;

  private static EmbeddedPostgres startPostgres() {
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
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "postgres");
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    POSTGRES.close();
  }

  @Test
  void auditEntityUsesMigratedSchemaAndPreservesJsonbObjects() {
    UUID id = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    logs.saveAndFlush(
        new AuditLogJpaEntity(
            id,
            UUID.randomUUID(),
            "TenantCreated",
            tenant,
            "Tenant",
            tenant,
            "iam-service",
            "{\"name\":\"Shop\",\"count\":2}",
            Instant.now(),
            Instant.now()));
    entityManager.clear();
    assertTrue(logs.findById(id).isPresent());
    assertEquals(
        "object",
        entityManager
            .createNativeQuery("SELECT jsonb_typeof(payload) FROM audit_logs WHERE id=:id")
            .setParameter("id", id)
            .getSingleResult());
    assertEquals(
        "Shop",
        entityManager
            .createNativeQuery("SELECT payload->>'name' FROM audit_logs WHERE id=:id")
            .setParameter("id", id)
            .getSingleResult());
    assertEquals(
        0,
        ((Number)
                entityManager
                    .createNativeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE success=false")
                    .getSingleResult())
            .intValue());
  }

  @Test
  void insertAutomaticallySetsCreatedAtWhenMissing() {
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-01T00:00:00Z");
    Instant beforeInsert = Instant.now();
    logs.saveAndFlush(
        new AuditLogJpaEntity(
            id,
            UUID.randomUUID(),
            "UserRegistered",
            null,
            "User",
            UUID.randomUUID(),
            "iam-service",
            "{}",
            occurredAt,
            null));
    entityManager.clear();

    var stored = logs.findById(id).orElseThrow();
    assertNotNull(stored.getCreatedAt());
    // PostgreSQL stores timestamps at microsecond precision.
    assertFalse(
        stored
            .getCreatedAt()
            .isBefore(beforeInsert.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    assertFalse(stored.getCreatedAt().isAfter(Instant.now()));
    assertEquals(occurredAt, stored.getOccurredAt());
  }

  @Test
  void timestampRenamePreservesExistingAuditRecords() throws Exception {
    var datasource = POSTGRES.getPostgresDatabase();
    org.flywaydb.core.Flyway.configure()
        .dataSource(datasource)
        .schemas("audit_timestamp_upgrade")
        .locations("classpath:db/migration")
        .target("1")
        .load()
        .migrate();
    UUID id = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-10-01T00:00:01Z");
    try (var connection = datasource.getConnection();
        var insert =
            connection.prepareStatement(
                """
            INSERT INTO audit_timestamp_upgrade.audit_logs
                (id,event_id,event_type,aggregate_type,aggregate_id,payload,occurred_at,recorded_at)
            VALUES (?,?,'UserRegistered','User',?,'{}',?,?)
            """)) {
      insert.setObject(1, id);
      insert.setObject(2, UUID.randomUUID());
      insert.setObject(3, UUID.randomUUID());
      insert.setTimestamp(4, java.sql.Timestamp.from(timestamp.minusSeconds(1)));
      insert.setTimestamp(5, java.sql.Timestamp.from(timestamp));
      insert.executeUpdate();
    }
    org.flywaydb.core.Flyway.configure()
        .dataSource(datasource)
        .schemas("audit_timestamp_upgrade")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    try (var connection = datasource.getConnection();
        var select =
            connection.prepareStatement(
                "SELECT created_at,occurred_at FROM audit_timestamp_upgrade.audit_logs WHERE id=?")) {
      select.setObject(1, id);
      try (var rows = select.executeQuery()) {
        assertTrue(rows.next());
        assertEquals(timestamp, rows.getTimestamp("created_at").toInstant());
        assertEquals(timestamp.minusSeconds(1), rows.getTimestamp("occurred_at").toInstant());
      }
    }
  }
}
