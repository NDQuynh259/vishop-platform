package com.vishop.notification.inbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vishop.notification.modules.notification.application.api.NotificationApi;
import com.vishop.notification.modules.notification.application.command.*;
import com.vishop.notification.modules.notification.application.dto.*;
import com.vishop.notification.modules.notification.application.port.NotificationInboxShopAccess;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationModuleConfiguration;
import com.vishop.notification.modules.notification.presentation.rest.controller.NotificationInboxController;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationMessage;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.pagination.CursorParams;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(
    classes = NotificationInboxPersistenceTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationInboxPersistenceTest {
  static final Instant NOW = Instant.parse("2026-11-07T12:00:00.123456Z");
  static EmbeddedPostgres postgres;

  static class TestClock extends Clock {
    volatile Instant at = NOW;

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

    @Bean
    @Primary
    NotificationInboxShopAccess testShops() {
      return mock(NotificationInboxShopAccess.class);
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
  @Autowired TestClock clock;
  @Autowired NotificationInboxShopAccess shops;
  @Autowired CommandBus commands;
  @Autowired QueryBus queries;
  @Autowired JdbcTemplate jdbc;

  @Autowired
  com.vishop.notification.modules.notification.domain.port.NotificationRepository repository;

  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), other = UUID.randomUUID();

  @BeforeEach
  void resetAccess() {
    clock.at = NOW;
    reset(shops);
    when(shops.allowedShops(any(), any())).thenReturn(Set.of());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  InboxActor actor(UUID userId, String... permissions) {
    return new InboxActor(tenant, userId, Set.of(permissions));
  }

  InboxFilter filter(int limit, String cursor) {
    return new InboxFilter(null, null, null, null, null, CursorParams.of(cursor, limit));
  }

  NotificationMessage message(String event, String permission, UUID shopId) {
    return new NotificationMessage(
        tenant,
        event,
        "STOCK_LOW",
        "SKU",
        event,
        permission,
        shopId,
        NotificationMessage.Severity.WARNING,
        "Low stock",
        "Check stock",
        "/inventory",
        NOW.minusSeconds(1));
  }

  UUID create(String event, String permission, UUID shopId, UUID... recipients) {
    return commands.dispatch(
        new CreateNotificationCommand(message(event, permission, shopId), Set.of(recipients)));
  }

  @Test
  void createPersistsPayloadAndIndependentRecipientStates() {
    UUID id = create("shared", "inventory.view", null, user, other);
    assertThat(id.version()).isEqualTo(7);
    var recipientIds =
        jdbc.queryForList(
            "SELECT id FROM notification_recipients WHERE notification_id=?", UUID.class, id);
    assertThat(recipientIds).hasSize(2).doesNotHaveDuplicates();
    assertThat(recipientIds)
        .allSatisfy(recipientId -> assertThat(recipientId.version()).isEqualTo(7));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE notification_id=?",
                Long.class,
                id))
        .isEqualTo(2);
    var a = actor(user, "inventory.view");
    var b = actor(other, "inventory.view");
    assertThat(inbox.getById(a, id).getTitle()).isEqualTo("Low stock");
    assertThat(inbox.unreadCount(a).getCount()).isEqualTo(1);
    var first = inbox.markRead(a, id);
    clock.at = NOW.plusSeconds(10);
    assertThat(inbox.markRead(a, id).getReadAt()).isEqualTo(first.getReadAt());
    assertThat(inbox.unreadCount(a).getCount()).isZero();
    assertThat(inbox.markAllRead(a, clock.at).getUpdatedCount()).isZero();
    assertThat(inbox.unreadCount(b).getCount()).isEqualTo(1);
    assertThat(inbox.getById(b, id).getReadAt()).isNull();
    inbox.archive(a, id);
    inbox.archive(a, id);
    assertThatThrownBy(() -> inbox.getById(a, id)).isInstanceOf(EntityNotFoundException.class);
    assertThat(inbox.list(a, filter(20, null)).items()).isEmpty();
    assertThat(inbox.getById(b, id).getId()).isEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM notifications WHERE id=?", Boolean.class, id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM notification_recipients WHERE notification_id=? AND user_id=?",
                Long.class,
                id,
                user))
        .isEqualTo(2);
  }

  @Test
  void visibilityAndMutationsAlwaysRespectTenantRecipientPermissionAndShopAccess() {
    UUID publicId = create("visible", "inventory.view", null, user);
    UUID finance = create("finance", "finance.settlement.view", null, user);
    UUID notRecipient = create("other", "inventory.view", null, other);
    UUID shop = UUID.randomUUID();
    UUID scoped = create("shop", "inventory.view", shop, user);
    var a = actor(user, "inventory.view");
    assertThat(inbox.list(a, filter(20, null)).items())
        .extracting(NotificationView::getId)
        .containsExactly(publicId);
    assertThat(inbox.unreadCount(a).getCount()).isEqualTo(1);
    for (UUID id : List.of(finance, notRecipient, scoped, UUID.randomUUID())) {
      assertThatThrownBy(() -> inbox.getById(a, id)).isInstanceOf(EntityNotFoundException.class);
      assertThatThrownBy(() -> inbox.markRead(a, id)).isInstanceOf(EntityNotFoundException.class);
      assertThatThrownBy(() -> inbox.archive(a, id)).isInstanceOf(EntityNotFoundException.class);
    }
    var foreign =
        new InboxActor(
            UUID.randomUUID(), user, Set.of("inventory.view", "finance.settlement.view"));
    assertThat(inbox.list(foreign, filter(20, null)).items()).isEmpty();
    assertThatThrownBy(() -> inbox.getById(foreign, publicId))
        .isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> inbox.markRead(foreign, publicId))
        .isInstanceOf(EntityNotFoundException.class);
    assertThatThrownBy(() -> inbox.archive(foreign, publicId))
        .isInstanceOf(EntityNotFoundException.class);
    assertThat(inbox.markAllRead(foreign, NOW).getUpdatedCount()).isZero();
    assertThat(inbox.unreadCount(actor(user)).getCount()).isZero();
    when(shops.allowedShops(tenant, user)).thenReturn(Set.of(shop));
    assertThat(inbox.getById(a, scoped).getShopId()).isEqualTo(shop);
    assertThat(inbox.unreadCount(a).getCount()).isEqualTo(2);
    assertThat(
            inbox
                .list(a, new InboxFilter(null, null, null, shop, null, CursorParams.of(null, 20)))
                .items())
        .extracting(NotificationView::getId)
        .containsExactly(scoped);
  }

  @Test
  void readAllHonorsCutoffAndDoesNotMarkHiddenOrLaterNotifications() {
    UUID older = create("older", "inventory.view", null, user);
    UUID hidden = create("hidden", "finance.settlement.view", null, user);
    UUID deniedShop = create("denied-shop", "inventory.view", UUID.randomUUID(), user);
    clock.at = NOW.plusSeconds(1);
    UUID later = create("later", "inventory.view", null, user);
    clock.at = NOW.plusSeconds(2);
    var a = actor(user, "inventory.view");
    assertThat(inbox.markAllRead(a, NOW).getUpdatedCount()).isEqualTo(1);
    assertThat(inbox.markAllRead(a, NOW).getUpdatedCount()).isZero();
    assertThat(inbox.getById(a, older).getReadAt()).isNotNull();
    assertThat(inbox.getById(a, later).getReadAt()).isNull();
    for (UUID id : List.of(hidden, deniedShop)) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT read_at IS NULL FROM notification_recipients WHERE notification_id=? AND"
                      + " user_id=?",
                  Boolean.class,
                  id,
                  user))
          .isTrue();
    }
    assertThatThrownBy(() -> inbox.markAllRead(a, clock.at.plusSeconds(1)))
        .isInstanceOf(BusinessRuleViolationException.class);
    jdbc.update(
        "UPDATE notifications SET deleted_at=? WHERE id=?",
        java.sql.Timestamp.from(clock.at),
        later);
    assertThat(inbox.unreadCount(a).getCount()).isZero();
  }

  @Test
  void cursorPaginationPreservesMicrosecondsAndTiesWithoutRepeats() {
    create("one", "inventory.view", null, user);
    create("two", "inventory.view", null, user);
    clock.at = NOW.plusNanos(1000);
    create("three", "inventory.view", null, user);
    var a = actor(user, "inventory.view");
    var ids = new HashSet<UUID>();
    String cursor = null;
    int pages = 0;
    do {
      var page = inbox.list(a, filter(1, cursor));
      assertThat(page.items()).hasSize(1);
      assertThat(ids.add(page.items().getFirst().getId())).isTrue();
      cursor = page.nextCursor();
      pages++;
      assertThat(pages).isLessThan(5);
    } while (cursor != null);
    assertThat(ids).hasSize(3);
    UUID one = ids.iterator().next();
    inbox.markRead(a, one);
    assertThat(
            inbox
                .list(
                    a,
                    new InboxFilter(
                        NotificationTypeCode.STOCK_LOW,
                        NotificationMessage.Severity.WARNING,
                        false,
                        null,
                        null,
                        CursorParams.of(null, 20)))
                .items())
        .hasSize(2);
    assertThatThrownBy(() -> inbox.list(a, filter(20, "invalid-cursor")))
        .isInstanceOf(io.github.coreplatform.common.domain.exception.BadRequestException.class);
  }

  @Test
  void offsetPaginationCountsOnlyMatchingVisibleRecipientsAndSupportsEmptyPages() {
    var a = actor(user, "inventory.view");
    UUID first = create("offset-first", "inventory.view", null, user);
    clock.at = NOW.plusSeconds(1);
    UUID second = create("offset-second", "inventory.view", null, user);
    clock.at = NOW.plusSeconds(2);
    UUID latest = create("offset-latest", "inventory.view", null, user);
    create("offset-other-user", "inventory.view", null, other);
    create("offset-private", "finance.settlement.view", null, user);
    create("offset-shop", "inventory.view", UUID.randomUUID(), user);
    UUID hidden = create("offset-hidden", "inventory.view", null, user);
    inbox.archive(a, hidden);
    var foreign =
        new NotificationMessage(
            UUID.randomUUID(),
            "offset-foreign",
            "STOCK_LOW",
            "SKU",
            "foreign",
            "inventory.view",
            null,
            NotificationMessage.Severity.WARNING,
            "Other tenant",
            "Check",
            null,
            NOW);
    inbox.create(foreign, Set.of(user));
    var page1 =
        inbox.list(a, new InboxFilter(null, null, null, null, OffsetPagination.of(1, 2), null));
    assertThat(page1.items()).extracting(NotificationView::getId).containsExactly(latest, second);
    assertThat(page1.total()).isEqualTo(3);
    assertThat(page1.totalPages()).isEqualTo(2);
    assertThat(page1.hasNextPage()).isTrue();
    assertThat(page1.nextCursor()).isNull();
    var page2 =
        inbox.list(a, new InboxFilter(null, null, null, null, OffsetPagination.of(2, 2), null));
    assertThat(page2.items()).extracting(NotificationView::getId).containsExactly(first);
    assertThat(page2.hasNextPage()).isFalse();
    var empty =
        inbox.list(a, new InboxFilter(null, null, null, null, OffsetPagination.of(3, 2), null));
    assertThat(empty.items()).isEmpty();
    assertThat(empty.total()).isEqualTo(3);
    inbox.markRead(a, latest);
    var unread =
        inbox.list(
            a,
            new InboxFilter(
                NotificationTypeCode.STOCK_LOW,
                NotificationMessage.Severity.WARNING,
                false,
                null,
                OffsetPagination.of(1, 20),
                null));
    assertThat(unread.total()).isEqualTo(2);
    assertThat(unread.items()).extracting(NotificationView::getId).containsExactly(second, first);
    var denied =
        inbox.list(
            actor(user), new InboxFilter(null, null, null, null, OffsetPagination.of(1, 20), null));
    assertThat(denied.total()).isZero();
    assertThat(denied.items()).isEmpty();
    assertThatThrownBy(
            () ->
                inbox.list(
                    a,
                    new InboxFilter(
                        null, null, null, null, OffsetPagination.of(Integer.MAX_VALUE, 100), null)))
        .isInstanceOf(io.github.coreplatform.common.domain.exception.BadRequestException.class);
  }

  @Test
  void duplicateEventsReturnOriginalIdWithoutRewritingPayloadRecipientsOrReadState() {
    var m = message("replay", "inventory.view", null);
    UUID id = inbox.create(m, Set.of(user));
    var a = actor(user, "inventory.view");
    var read = inbox.markRead(a, id).getReadAt();
    assertThat(inbox.create(m, Set.of(user, other))).isEqualTo(id);
    assertThat(inbox.getById(a, id).getReadAt()).isEqualTo(read);
    assertThatThrownBy(() -> inbox.getById(actor(other, "inventory.view"), id))
        .isInstanceOf(EntityNotFoundException.class);
    var changed =
        new NotificationMessage(
            tenant,
            m.sourceEventId(),
            m.type(),
            m.subjectType(),
            m.subjectId(),
            m.requiredPermission(),
            null,
            m.severity(),
            "Changed",
            m.body(),
            m.actionUrl(),
            m.occurredAt());
    assertThatThrownBy(() -> inbox.create(changed, Set.of(user)))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE tenant_id=?", Long.class, tenant))
        .isEqualTo(1);
  }

  @Test
  void concurrentCreationAndReadAreIdempotent() throws Exception {
    var message = message("race", "inventory.view", null);
    var start = new CountDownLatch(1);
    UUID id;
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<UUID> create =
          () -> {
            start.await();
            return inbox.create(message, Set.of(user));
          };
      var first = pool.submit(create);
      var second = pool.submit(create);
      start.countDown();
      id = first.get(15, TimeUnit.SECONDS);
      assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(id);
    }
    var reads = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      UUID notificationId = id;
      Callable<Instant> read =
          () -> {
            reads.await();
            return inbox.markRead(actor(user, "inventory.view"), notificationId).getReadAt();
          };
      var first = pool.submit(read);
      var second = pool.submit(read);
      reads.countDown();
      assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(first.get(15, TimeUnit.SECONDS));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM notification_recipients WHERE notification_id=? AND user_id=?",
                Long.class,
                id,
                user))
        .isEqualTo(1);
  }

  @Test
  void recipientWriteFailureRollsBackTheWholeNotification() {
    UUID id = io.github.coreplatform.common.domain.UuidV7.nextId();
    var notification =
        new com.vishop.notification.modules.notification.domain.entity.Notification(
            id, message("rollback", "inventory.view", null), NOW);
    var recipient =
        new com.vishop.notification.modules.notification.domain.entity.NotificationRecipient(
            io.github.coreplatform.common.domain.UuidV7.nextId(),
            tenant,
            id,
            user,
            NOW,
            null,
            null);
    var duplicateRecipient =
        new com.vishop.notification.modules.notification.domain.entity.NotificationRecipient(
            io.github.coreplatform.common.domain.UuidV7.nextId(),
            tenant,
            id,
            user,
            NOW,
            null,
            null);
    assertThatThrownBy(
            () -> repository.append(notification, List.of(recipient, duplicateRecipient)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE notification_id=?",
                Long.class,
                id))
        .isZero();
  }

  @Test
  void invalidSourcePayloadsDoNotCreateRows() {
    var m = message("invalid", "inventory.view", null);
    for (String action :
        List.of(
            "https://example.com", "//example.com", "javascript:alert(1)", "/\\\\example.com")) {
      var invalid =
          new NotificationMessage(
              tenant,
              m.sourceEventId(),
              m.type(),
              m.subjectType(),
              m.subjectId(),
              m.requiredPermission(),
              null,
              m.severity(),
              m.title(),
              m.body(),
              action,
              m.occurredAt());
      assertThatThrownBy(() -> inbox.create(invalid, Set.of(user)))
          .isInstanceOf(BusinessRuleViolationException.class);
    }
    var unknown =
        new NotificationMessage(
            tenant,
            "unknown",
            "UNKNOWN_TYPE",
            "SKU",
            "1",
            "inventory.view",
            null,
            m.severity(),
            m.title(),
            m.body(),
            null,
            m.occurredAt());
    assertThatThrownBy(() -> inbox.create(unknown, Set.of(user)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE tenant_id=?", Long.class, tenant))
        .isZero();
  }

  @Test
  void httpEndpointsUseActorAndExposeOnlyRecipientOperations() throws Exception {
    UUID id = create("http", "inventory.view", null, user);
    var principal = new GatewayPrincipal(user, tenant, "USER", "MEMBER", Set.of("inventory.view"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    var mvc =
        MockMvcBuilders.standaloneSetup(new NotificationInboxController(commands, queries))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    String path = "/api/v1/notifications";
    mvc.perform(
            get(path)
                .param("tenantId", UUID.randomUUID().toString())
                .param("userId", other.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(id.toString()));
    mvc.perform(get(path + "/unread-count"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1));
    mvc.perform(patch(path + "/" + id + "/read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").exists());
    mvc.perform(
            post(path + "/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"before\":\"2026-11-07T12:00:00.123456Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updatedCount").value(0));
    mvc.perform(delete(path + "/" + id)).andExpect(status().isNoContent());
    mvc.perform(get(path + "/" + id)).andExpect(status().isNotFound());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }
}
