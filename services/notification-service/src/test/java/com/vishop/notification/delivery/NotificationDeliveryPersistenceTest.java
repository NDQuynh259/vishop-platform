package com.vishop.notification.delivery;

import static org.assertj.core.api.Assertions.*;

import com.vishop.notification.modules.notification.application.api.NotificationDeliveryApi;
import com.vishop.notification.modules.notification.application.command.EnqueueDeliveriesCommand;
import com.vishop.notification.modules.notification.domain.entity.NotificationDelivery;
import com.vishop.notification.modules.notification.domain.port.NotificationDeliveryRepository;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationModuleConfiguration;
import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationDeliveryJpaEntity;
import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationJpaEntity;
import com.vishop.notification.modules.notification.infrastructure.persistence.entities.NotificationRecipientJpaEntity;
import com.vishop.notification.modules.notification.infrastructure.persistence.repositories.SpringDataNotificationDeliveryRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.NotificationChannelSettingJpaEntity;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = NotificationDeliveryPersistenceTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationDeliveryPersistenceTest {
  static final Instant NOW = Instant.parse("2026-11-09T00:00:00.123456Z");
  static EmbeddedPostgres postgres;

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(NotificationModuleConfiguration.class)
  static class Config {}

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

  @Autowired NotificationDeliveryRepository repository;
  @Autowired SpringDataNotificationDeliveryRepository jpa;
  @Autowired CommandBus commands;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactions;
  @Autowired jakarta.persistence.EntityManager entityManager;
  UUID tenant = UuidV7.nextId(), notification = UuidV7.nextId();

  @BeforeEach
  void content() {
    db.update(
        """
        INSERT INTO notifications(id,tenant_id,source_event_id,type,subject_type,subject_id,
        required_permission,severity,title,body,occurred_at)
        VALUES (?,?,?,'STOCK_LOW','SKU','sku-1','inventory.view','WARNING','Title','Body',now())
        """,
        notification,
        tenant,
        UuidV7.nextId().toString());
  }

  private NotificationDelivery pending(String destination) {
    return NotificationDelivery.pending(
        UuidV7.nextId(), tenant, notification, null, NotificationChannel.EMAIL, destination, NOW);
  }

  private int count(String table) {
    return db.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id=?", Integer.class, tenant);
  }

  @Test
  void commandPersistsExternalChannelsWithOptionalUsersAndConnectionBinding() {
    UUID setting = UuidV7.nextId(), user = UuidV7.nextId();
    db.update(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
        setting,
        tenant);
    List<UUID> ids =
        commands.dispatch(
            new EnqueueDeliveriesCommand(
                tenant,
                notification,
                List.of(
                    new NotificationDeliveryApi.Target(
                        user, NotificationChannel.EMAIL, " Member@example.test ", setting),
                    new NotificationDeliveryApi.Target(
                        user, NotificationChannel.EMAIL, "member@EXAMPLE.test", setting),
                    new NotificationDeliveryApi.Target(
                        null, NotificationChannel.TELEGRAM, "-100123"),
                    new NotificationDeliveryApi.Target(
                        null, NotificationChannel.LARK, "https://example.test/Webhook"))));
    assertThat(ids).hasSize(3);
    var email = repository.findById(tenant, ids.getFirst()).orElseThrow();
    assertThat(email.userId()).isEqualTo(user);
    assertThat(email.settingId()).isEqualTo(setting);
    assertThat(email.destination()).isEqualTo("Member@example.test");
    var stored = jpa.findByTenantIdAndId(tenant, email.id()).orElseThrow();
    assertThat(stored.getTemplateCode()).isEqualTo("GENERIC_NOTIFICATION");
    assertThat(stored.getTemplateData()).isEmpty();
    assertThat(stored.getDestinationKey()).hasSize(64);
    assertThat(stored.getVersion()).isZero();
    assertThat(stored.getCreatedAt()).isNotNull();
    assertThat(stored.getUpdatedAt()).isNotNull();
    assertThat(repository.findById(UuidV7.nextId(), email.id())).isEmpty();
    assertThat(repository.findById(tenant, ids.get(1)).orElseThrow().userId()).isNull();
    assertThat(repository.findById(tenant, ids.get(2)).orElseThrow().channel())
        .isEqualTo(NotificationChannel.LARK);
  }

  @Test
  void domainStatesRoundTripThroughJpaWithoutLosingLeasesOrAttemptBudgets() {
    var pending = pending("pending@example.test");
    var processing =
        pending("processing@example.test").claim(UuidV7.nextId(), NOW, Duration.ofMinutes(2));
    var claimed = pending("sent@example.test").claim(UuidV7.nextId(), NOW, Duration.ofMinutes(2));
    var sent = claimed.complete(claimed.lockToken(), NOW.plusSeconds(1));
    var failedClaim =
        pending("failed@example.test").claim(UuidV7.nextId(), NOW, Duration.ofMinutes(2));
    var failed = failedClaim.fail(failedClaim.lockToken(), NOW.plusSeconds(1), null);
    for (var delivery : List.of(pending, processing, sent, failed)) {
      repository.append(List.of(delivery));
      assertThat(repository.findById(tenant, delivery.id())).contains(delivery);
    }
  }

  @Test
  void bidirectionalJpaAssociationsResolveParentsAndLazyCollectionsFromScalarIds() {
    UUID setting = UuidV7.nextId(), recipient = UuidV7.nextId(), user = UuidV7.nextId();
    db.update(
        "INSERT INTO notification_settings(id,tenant_id,channel,name) VALUES (?,?,'EMAIL','Mail connection')",
        setting,
        tenant);
    db.update(
        "INSERT INTO notification_recipients(id,tenant_id,notification_id,user_id) VALUES (?,?,?,?)",
        recipient,
        tenant,
        notification,
        user);
    var bound =
        NotificationDelivery.pending(
            UuidV7.nextId(),
            tenant,
            notification,
            user,
            NotificationChannel.EMAIL,
            "bound@example.test",
            NOW,
            setting);
    var unbound = pending("unbound@example.test");
    repository.append(List.of(bound, unbound));

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              entityManager.clear();
              var entity = entityManager.find(NotificationDeliveryJpaEntity.class, bound.id());
              assertThat(entity.getNotification().getId()).isEqualTo(notification);
              assertThat(entity.getNotification().getTenantId()).isEqualTo(tenant);
              assertThat(entity.getNotification().getTitle()).isEqualTo("Title");
              assertThat(
                      org.hibernate.Hibernate.isInitialized(
                          entity.getNotification().getDeliveries()))
                  .isFalse();
              assertThat(
                      org.hibernate.Hibernate.isInitialized(
                          entity.getNotification().getRecipients()))
                  .isFalse();
              assertThat(entity.getNotification().getDeliveries())
                  .extracting(NotificationDeliveryJpaEntity::getId)
                  .containsExactlyInAnyOrder(bound.id(), unbound.id());
              assertThat(entity.getNotification().getRecipients())
                  .extracting(NotificationRecipientJpaEntity::getId)
                  .containsExactly(recipient);
              assertThat(entity.getSetting().getId()).isEqualTo(setting);
              assertThat(entity.getSetting().getTenantId()).isEqualTo(tenant);
              assertThat(entity.getSetting().getChannel()).isEqualTo(NotificationChannel.EMAIL);
              assertThat(entity.getSetting().getName()).isEqualTo("Mail connection");
              assertThat(org.hibernate.Hibernate.isInitialized(entity.getSetting().getDeliveries()))
                  .isFalse();
              assertThat(entity.getSetting().getDeliveries())
                  .extracting(NotificationDeliveryJpaEntity::getId)
                  .containsExactly(bound.id());
              assertThat(
                      entityManager
                          .find(NotificationDeliveryJpaEntity.class, unbound.id())
                          .getSetting())
                  .isNull();
              assertThat(
                      entityManager
                          .find(NotificationRecipientJpaEntity.class, recipient)
                          .getNotification()
                          .getId())
                  .isEqualTo(notification);
              entityManager.remove(entity);
              entityManager.flush();
              entityManager.clear();
              assertThat(
                      entityManager.find(NotificationJpaEntity.class, notification).getDeliveries())
                  .extracting(NotificationDeliveryJpaEntity::getId)
                  .containsExactly(unbound.id());
              assertThat(
                      entityManager
                          .find(NotificationChannelSettingJpaEntity.class, setting)
                          .getDeliveries())
                  .isEmpty();
            });
    assertThat(repository.findById(tenant, bound.id())).isEmpty();
    assertThat(count("notifications")).isEqualTo(1);
    assertThat(count("notification_settings")).isEqualTo(1);
    assertThat(count("notification_recipients")).isEqualTo(1);
  }

  @Test
  void foreignTenantBindingRollsBackTheWholeBatch() {
    var valid = pending("valid@example.test");
    var foreign =
        NotificationDelivery.pending(
            UuidV7.nextId(),
            UuidV7.nextId(),
            notification,
            null,
            NotificationChannel.EMAIL,
            "foreign@example.test",
            NOW);
    assertThatThrownBy(() -> repository.append(List.of(valid, foreign)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(repository.findById(tenant, valid.id())).isEmpty();
    UUID foreignSetting = UuidV7.nextId();
    db.update(
        "INSERT INTO notification_settings(id,tenant_id,channel) VALUES (?,?,'EMAIL')",
        foreignSetting,
        UuidV7.nextId());
    var wrongSetting =
        NotificationDelivery.pending(
            UuidV7.nextId(),
            tenant,
            notification,
            null,
            NotificationChannel.EMAIL,
            "setting@example.test",
            NOW,
            foreignSetting);
    assertThatThrownBy(() -> repository.append(List.of(wrongSetting)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void larkDeliveriesBindToTheCorrectGroupWhenTenantHasMultipleSettings() {
    UUID firstSetting = UuidV7.nextId(), secondSetting = UuidV7.nextId();
    db.update(
        "INSERT INTO notification_settings(id,tenant_id,channel,name) VALUES (?,?,'LARK','Operations'), (?,?,'LARK','Accounting')",
        firstSetting,
        tenant,
        secondSetting,
        tenant);
    List<UUID> ids =
        commands.dispatch(
            new EnqueueDeliveriesCommand(
                tenant,
                notification,
                List.of(
                    new NotificationDeliveryApi.Target(
                        null,
                        NotificationChannel.LARK,
                        "https://example.test/operations",
                        firstSetting),
                    new NotificationDeliveryApi.Target(
                        null,
                        NotificationChannel.LARK,
                        "https://example.test/accounting",
                        secondSetting))));
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              var first = entityManager.find(NotificationDeliveryJpaEntity.class, ids.get(0));
              var second = entityManager.find(NotificationDeliveryJpaEntity.class, ids.get(1));
              assertThat(first.getSetting().getId()).isEqualTo(firstSetting);
              assertThat(second.getSetting().getId()).isEqualTo(secondSetting);
              assertThat(first.getSetting().getDeliveries())
                  .extracting(NotificationDeliveryJpaEntity::getId)
                  .containsExactly(first.getId());
              assertThat(second.getSetting().getDeliveries())
                  .extracting(NotificationDeliveryJpaEntity::getId)
                  .containsExactly(second.getId());
            });
  }

  @Test
  void duplicateCanonicalDestinationIsRejectedWithoutPartiallySavingBatch() {
    var first = pending("same@example.test");
    var second = pending("SAME@example.test");
    assertThatThrownBy(() -> repository.append(List.of(first, second)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("notification_deliveries")).isZero();
  }

  @Test
  void deliveriesParticipateInTheOuterTransaction() {
    var delivery = pending("rollback@example.test");
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        ignored -> {
                          repository.append(List.of(delivery));
                          throw new IllegalStateException("rollback");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("notification_deliveries")).isZero();
  }

  @Test
  void versionRejectsStaleUpdatesAndRetainsExistingTemplateAndErrorHistory() {
    var delivery = pending("version@example.test");
    repository.append(List.of(delivery));
    db.update(
        "UPDATE notification_deliveries SET template_code='LEGACY',template_version=3,template_data='{\"keep\":true}',last_error='legacy' WHERE id=?",
        delivery.id());
    var first = jpa.findByTenantIdAndId(tenant, delivery.id()).orElseThrow();
    var stale = jpa.findByTenantIdAndId(tenant, delivery.id()).orElseThrow();
    first.setStatus(NotificationDelivery.Status.CANCELLED);
    first.setLastError("must-not-overwrite-history");
    var updated = jpa.saveAndFlush(first);
    assertThat(updated.getVersion()).isEqualTo(1);
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(first.getCreatedAt());
    stale.setStatus(NotificationDelivery.Status.FAILED);
    assertThatThrownBy(() -> jpa.saveAndFlush(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    var persisted = jpa.findByTenantIdAndId(tenant, delivery.id()).orElseThrow();
    assertThat(persisted.getTemplateCode()).isEqualTo("LEGACY");
    assertThat(persisted.getTemplateVersion()).isEqualTo(3);
    assertThat(persisted.getTemplateData()).containsEntry("keep", true);
    assertThat(persisted.getLastError()).isEqualTo("legacy");
  }
}
