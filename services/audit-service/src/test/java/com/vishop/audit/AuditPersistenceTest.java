package com.vishop.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.vishop.audit.domain.AuditLogEntity;
import com.vishop.audit.domain.AuditLogRepository;
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
  @Autowired AuditLogRepository logs;
  @Autowired EntityManager entityManager;

  private static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().setPort(0).start();
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
        new AuditLogEntity(
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
}
