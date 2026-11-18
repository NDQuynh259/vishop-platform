package com.vishop.notification.inbox;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.notification.application.dto.NotificationCreated;
import com.vishop.notification.modules.notification.infrastructure.config.NotificationRealtimeConfiguration;
import com.vishop.notification.modules.notification.presentation.realtime.NotificationSseStreams;
import com.vishop.notification.modules.notification.presentation.rest.controller.NotificationStreamController;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import redis.embedded.RedisServer;

/** Two independent Spring instances, one shared database and one real Redis server. */
class NotificationRedisFanoutTest {
  static RedisServer redis;
  static EmbeddedPostgres postgres;
  static int port;
  AnnotationConfigApplicationContext a, b;
  final UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), id = UUID.randomUUID();

  @BeforeAll
  static void infrastructure() throws Exception {
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    redis =
        RedisServer.newRedisServer()
            .port(port)
            .setting("bind 127.0.0.1")
            .setting("maxmemory 64M")
            .build();
    redis.start();
    postgres = EmbeddedPostgres.builder().setPort(0).start();
    new JdbcTemplate(postgres.getPostgresDatabase())
        .execute(
            "CREATE TABLE realtime_test_notifications(id uuid PRIMARY KEY, tenant_id uuid NOT NULL)");
  }

  @AfterAll
  static void stopInfrastructure() throws Exception {
    try {
      if (redis != null) redis.stop();
    } finally {
      if (postgres != null) postgres.close();
    }
  }

  @BeforeEach
  void startInstances() {
    String channel = "test:notification:" + UUID.randomUUID();
    a = instance(channel);
    b = instance(channel);
  }

  @AfterEach
  void stopInstances() {
    SecurityContextHolder.clearContext();
    try {
      if (b != null) b.close();
    } finally {
      if (a != null) a.close();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  @Import({NotificationRealtimeConfiguration.class, NotificationStreamController.class})
  static class Instance {
    @Bean
    RedisConnectionFactory connectionFactory() {
      var client =
          LettuceClientConfiguration.builder()
              .commandTimeout(Duration.ofSeconds(1))
              .shutdownTimeout(Duration.ZERO)
              .build();
      return new LettuceConnectionFactory(
          new RedisStandaloneConfiguration("127.0.0.1", port), client);
    }

    @Bean
    StringRedisTemplate redisTemplate(RedisConnectionFactory factory) {
      return spy(new StringRedisTemplate(factory));
    }

    @Bean
    ObjectMapper json() {
      return new ObjectMapper();
    }

    @Bean(destroyMethod = "close")
    NotificationSseStreams streams() {
      return new NotificationSseStreams(Clock.systemUTC());
    }

    @Bean
    DataSource dataSource() {
      return postgres.getPostgresDatabase();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource source) {
      return new DataSourceTransactionManager(source);
    }
  }

  AnnotationConfigApplicationContext instance(String channel) {
    var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource("instance", Map.of("notification.realtime.channel", channel)));
    context.register(Instance.class);
    context.refresh();
    return context;
  }

  @Test
  void committedCreationReachesEveryAuthorizedTabOnBothInstancesExactlyOnce() throws Exception {
    var a1 = subscribe(a, tenant, user, "inventory.view");
    var a2 = subscribe(a, tenant, user, "inventory.view");
    var b1 = subscribe(b, tenant, user, "inventory.view");
    var wrongTenant = subscribe(b, UUID.randomUUID(), user, "inventory.view");
    var wrongUser = subscribe(b, tenant, UUID.randomUUID(), "inventory.view");
    var wrongPermission = subscribe(b, tenant, user, "finance.view");
    transaction(a)
        .executeWithoutResult(
            status -> {
              persistAndPublish(a);
              verify(a.getBean(StringRedisTemplate.class), never())
                  .convertAndSend(anyString(), any());
              assertThat(body(a1)).doesNotContain("event:notification.created");
              assertThat(body(b1)).doesNotContain("event:notification.created");
            });
    assertThat(rowCount(b)).isEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              for (var client : List.of(a1, a2, b1)) {
                assertThat(body(client))
                    .contains("event:notification.created", id.toString(), tenant.toString())
                    .doesNotContain(
                        user.toString(), "inventory.view", "recipients", "requiredPermission");
              }
            });
    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              for (var client : List.of(a1, a2, b1))
                assertThat(count(client, "notification.created")).isEqualTo(1);
              for (var client : List.of(wrongTenant, wrongUser, wrongPermission)) {
                assertThat(count(client, "notification.created")).isZero();
              }
            });
    verify(a.getBean(StringRedisTemplate.class)).convertAndSend(anyString(), any());
    verify(b.getBean(StringRedisTemplate.class), never()).convertAndSend(anyString(), any());
  }

  @Test
  void rollbackAndEventsOutsideATransactionAreNotPublished() throws Exception {
    var client = subscribe(b, tenant, user, "inventory.view");
    transaction(a)
        .executeWithoutResult(
            status -> {
              persistAndPublish(a);
              status.setRollbackOnly();
            });
    a.publishEvent(event());
    assertThat(rowCount(b)).isZero();
    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              verify(a.getBean(StringRedisTemplate.class), never())
                  .convertAndSend(anyString(), any());
              assertThat(count(client, "notification.created")).isZero();
            });
  }

  @Test
  void redisResubscriptionRequestsRestRefreshOnTheExistingSseConnection() throws Exception {
    var source = subscribe(a, tenant, user, "inventory.view");
    var client = subscribe(b, tenant, user, "inventory.view");
    var listener = b.getBean(RedisMessageListenerContainer.class);
    listener.stop();
    long previousReady = count(client, "ready");
    transaction(a).executeWithoutResult(status -> persistAndPublish(a));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(body(source)).contains(id.toString()));
    assertThat(count(client, "notification.created")).isZero();
    listener.start();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(count(client, "ready")).isGreaterThan(previousReady));
    assertThat(client.getRequest().isAsyncStarted()).isTrue();
    assertThat(rowCount(b)).isEqualTo(1);
    // Pub/Sub has no replay. ready tells this client to reconcile through the normal REST API.
    assertThat(count(client, "notification.created")).isZero();
  }

  @Test
  void failedRedisPublicationDoesNotUndoTheCommittedNotification() throws Exception {
    var client = subscribe(a, tenant, user, "inventory.view");
    long previousReady = count(client, "ready");
    doThrow(new RedisConnectionFailureException("Unavailable"))
        .when(a.getBean(StringRedisTemplate.class))
        .convertAndSend(anyString(), any());
    assertThatCode(() -> transaction(a).executeWithoutResult(status -> persistAndPublish(a)))
        .doesNotThrowAnyException();
    assertThat(rowCount(b)).isEqualTo(1);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(count(client, "ready")).isGreaterThan(previousReady));
    assertThat(count(client, "notification.created")).isZero();
  }

  NotificationCreated event() {
    return new NotificationCreated(id, tenant, Set.of(user), "inventory.view");
  }

  void persistAndPublish(AnnotationConfigApplicationContext context) {
    new JdbcTemplate(context.getBean(DataSource.class))
        .update("INSERT INTO realtime_test_notifications(id,tenant_id) VALUES (?,?)", id, tenant);
    context.publishEvent(event());
  }

  int rowCount(AnnotationConfigApplicationContext context) {
    return new JdbcTemplate(context.getBean(DataSource.class))
        .queryForObject(
            "SELECT count(*) FROM realtime_test_notifications WHERE id=?", Integer.class, id);
  }

  TransactionTemplate transaction(AnnotationConfigApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  MvcResult subscribe(
      AnnotationConfigApplicationContext context, UUID tenantId, UUID userId, String permission)
      throws Exception {
    var actor = new GatewayPrincipal(userId, tenantId, "USER", "MEMBER", Set.of(permission));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    try {
      var mvc =
          MockMvcBuilders.standaloneSetup(context.getBean(NotificationStreamController.class))
              .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
              .build();
      var result =
          mvc.perform(get(NotificationStreamHttpTest.PATH))
              .andExpect(status().isOk())
              .andExpect(request().asyncStarted())
              .andReturn();
      await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(body(result)).contains("event:ready"));
      return result;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  static String body(MvcResult client) {
    return new String(client.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  static long count(MvcResult client, String event) {
    return body(client).lines().filter(line -> line.equals("event:" + event)).count();
  }
}
