package com.vishop.notification.inbox;

import static org.assertj.core.api.Assertions.*;

import com.vishop.notification.modules.notification.application.api.NotificationApi;
import com.vishop.notification.modules.notification.application.dto.InboxActor;
import com.vishop.notification.modules.notification.application.service.NotificationRetentionService;
import com.vishop.notification.modules.notification.domain.port.NotificationRecipientRetentionRepository;
import com.vishop.notification.modules.notification.domain.valueobject.NotificationRetentionPolicy;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationModuleConfiguration;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = "notification.realtime.transport=local",
    classes = NotificationRetentionPersistenceTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationRetentionPersistenceTest {
  static final Instant NOW = Instant.parse("2026-11-18T12:00:00.123456Z");
  static final Duration RETENTION = Duration.ofDays(90);
  static final Instant CUTOFF = NOW.minus(RETENTION);
  static EmbeddedPostgres postgres;

  static class TestClock extends Clock {
    Instant at = NOW;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return at;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(NotificationModuleConfiguration.class)
  static class Config {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) throws Exception {
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    String url = postgres.getJdbcUrl("postgres", "postgres");
    Flyway.configure().dataSource(url, "postgres", "postgres").load().migrate();
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "postgres");
    properties.add("spring.flyway.enabled", () -> false);
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    properties.add("spring.jpa.open-in-view", () -> false);
  }

  @AfterAll
  static void stop() throws Exception {
    if (postgres != null) postgres.close();
  }

  @Autowired NotificationApi inbox;
  @Autowired NotificationRecipientRetentionRepository repository;
  @Autowired TestClock clock;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), other = UUID.randomUUID();

  @BeforeEach
  void reset() {
    jdbc.execute("TRUNCATE TABLE notifications CASCADE");
    clock.at = NOW;
  }

  InboxActor actor(UUID userId) {
    return new InboxActor(tenant, userId, Set.of("inventory.view"));
  }

  NotificationMessage message(String event) {
    return new NotificationMessage(
        tenant,
        event,
        "STOCK_LOW",
        "SKU",
        event,
        "inventory.view",
        null,
        NotificationMessage.Severity.WARNING,
        "Low stock",
        "Check stock",
        "/inventory",
        clock.at);
  }

  UUID create(String event, Instant created, UUID... users) {
    clock.at = created;
    return inbox.create(message(event), Set.of(users));
  }

  long recipients(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification_recipients WHERE notification_id=?", Long.class, id);
  }

  @Test
  void purgesVisibleAtInclusiveCreationCutoffIncludingUnreadButUsesDeletionTimeForArchived() {
    UUID oldUnread = create("old-unread", CUTOFF.minusSeconds(1), user);
    UUID boundaryRead = create("boundary-read", CUTOFF, user);
    inbox.markRead(actor(user), boundaryRead);
    UUID fresh = create("fresh", CUTOFF.plusNanos(1000), user);
    UUID oldArchived = create("old-archived", CUTOFF.minusSeconds(100), user);
    clock.at = CUTOFF;
    inbox.archive(actor(user), oldArchived);
    UUID recentlyArchived = create("recent-archive", CUTOFF.minusSeconds(100), user);
    clock.at = NOW;
    inbox.archive(actor(user), recentlyArchived);

    var result =
        new NotificationRetentionService(repository, clock)
            .purge(new NotificationRetentionPolicy(RETENTION, RETENTION, 500, 10));
    assertThat(result.visibleDeleted()).isEqualTo(2);
    assertThat(result.archivedDeleted()).isEqualTo(1);
    for (UUID id : List.of(oldUnread, boundaryRead, oldArchived))
      assertThat(recipients(id)).isZero();
    assertThat(recipients(fresh)).isEqualTo(1);
    assertThat(recipients(recentlyArchived)).isEqualTo(1);
    assertThat(inbox.unreadCount(actor(user)).getCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Long.class)).isEqualTo(5);
  }

  @Test
  void softDeleteThenPurgePreservesOtherRecipientDeliveryAndReplayIdentity() {
    UUID id = create("shared", CUTOFF.minusSeconds(1), user, other);
    var original = message("shared");
    inbox.archive(actor(user), id);
    inbox.archive(actor(user), id);
    assertThat(recipients(id)).isEqualTo(2);
    jdbc.update(
        "INSERT INTO notification_deliveries(id,tenant_id,notification_id,user_id,destination) VALUES (?,?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        id,
        user,
        "retention@example.com");
    // Give the other recipient a later inbox creation time; only the archived recipient expires.
    jdbc.update(
        "UPDATE notification_recipients SET created_at=? WHERE notification_id=? AND user_id=?",
        java.sql.Timestamp.from(NOW),
        id,
        other);
    assertThat(repository.purgeArchivedBefore(CUTOFF, 500)).isEqualTo(1);
    assertThat(recipients(id)).isEqualTo(1);
    assertThat(inbox.getById(actor(other), id).getId()).isEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_deliveries WHERE notification_id=?",
                Long.class,
                id))
        .isEqualTo(1);
    clock.at = NOW;
    assertThat(inbox.create(original, Set.of(user, other))).isEqualTo(id);
    assertThat(recipients(id)).isEqualTo(1);
    assertThatThrownBy(() -> inbox.archive(actor(user), id))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void competingWorkerSkipsLockedRowsAndEachBoundedBatchCommitsIndependently() {
    UUID locked = create("locked", CUTOFF.minusSeconds(2), user);
    UUID free = create("free", CUTOFF.minusSeconds(1), user);
    UUID next = create("next-batch", CUTOFF, user);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              jdbc.queryForObject(
                  "SELECT id FROM notification_recipients WHERE notification_id=? FOR UPDATE",
                  UUID.class,
                  locked);
              // REQUIRES_NEW suspends this transaction; this simulates another worker holding its
              // row lock.
              assertThat(repository.purgeVisibleBefore(CUTOFF, 1)).isEqualTo(1);
              assertThat(recipients(locked)).isEqualTo(1);
              assertThat(recipients(free)).isZero();
              assertThat(recipients(next)).isEqualTo(1);
              status.setRollbackOnly();
            });
    assertThat(recipients(free)).isZero();
    assertThat(repository.purgeVisibleBefore(CUTOFF, 1)).isEqualTo(1);
    assertThat(recipients(next)).isEqualTo(1);
    assertThat(repository.purgeVisibleBefore(CUTOFF, 1)).isEqualTo(1);
    assertThat(repository.purgeVisibleBefore(CUTOFF, 1)).isZero();
  }

  @Test
  void migrationCreatesSeparatePartialIndexesForEachRetentionScope() {
    var definitions =
        jdbc.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename='notification_recipients'"
                + " AND indexname IN ('idx_notification_recipient_visible_retention','idx_notification_recipient_archived_retention')",
            String.class);
    assertThat(definitions).hasSize(2);
    assertThat(definitions)
        .anySatisfy(
            sql -> assertThat(sql).contains("(created_at, id)", "WHERE (deleted_at IS NULL)"));
    assertThat(definitions)
        .anySatisfy(
            sql -> assertThat(sql).contains("(deleted_at, id)", "WHERE (deleted_at IS NOT NULL)"));
  }
}
