package com.vishop.notification.setting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.*;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.config.NotificationSettingModuleConfiguration;
import com.vishop.notification.modules.setting.infrastructure.persistence.NotificationSettingJpaAdapter;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.*;
import com.vishop.notification.modules.setting.infrastructure.persistence.model.*;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.modules.setting.presentation.rest.NotificationSettingController;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.exception.GlobalExceptionHandler;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(
    classes = NotificationSettingPersistenceTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationSettingPersistenceTest {
  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({NotificationSettingModuleConfiguration.class, NotificationSettingJpaAdapter.class})
  static class Config {}

  static EmbeddedPostgres postgres;

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

  @Autowired NotificationSettingManagementApi settings;
  @Autowired NotificationSettingManagementRepository repository;
  @Autowired SpringDataNotificationChannelSettingRepository rows;
  @Autowired ObjectMapper json;
  final UUID tenant = UUID.randomUUID();

  @Test
  void missingRowsUseChannelDefaults() {
    assertThat(settings.channels(tenant)).hasSize(4);
    var inApp = settings.channel(tenant, NotificationChannel.IN_APP);
    assertThat(inApp.configured()).isFalse();
    assertThat(inApp.enabled()).isTrue();
    assertThat(inApp.version()).isZero();
    assertThat(settings.channel(tenant, NotificationChannel.EMAIL).enabled()).isFalse();
    assertThat(settings.channel(tenant, NotificationChannel.TELEGRAM).enabled()).isFalse();
    var lark = settings.channel(tenant, NotificationChannel.LARK);
    assertThat(lark.enabled()).isFalse();
    assertThat(lark.configured()).isFalse();
    assertThat(lark.available()).isFalse();
  }

  @Test
  void tenantIsolationAndAuditVersionArePreserved() {
    var first =
        settings.changeChannel(
            tenant, NotificationChannel.IN_APP, new ChannelChange(false, null, null, null, 0));
    assertThat(first.version()).isEqualTo(1);
    assertThat(settings.channel(UUID.randomUUID(), NotificationChannel.IN_APP).enabled()).isTrue();
    var id =
        rows.findByTenantIdAndChannel(tenant, NotificationChannel.IN_APP).orElseThrow().getId();
    var entity = rows.findById(id).orElseThrow();
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(entity.getTenantId()).isEqualTo(tenant);
    assertThat(entity.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    var createdAt = entity.getCreatedAt();
    assertThat(createdAt).isNotNull();
    assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    var next =
        settings.changeChannel(
            tenant,
            NotificationChannel.IN_APP,
            new ChannelChange(false, null, null, null, first.version()));
    assertThat(next.version()).isEqualTo(2);
    assertThat(rows.findById(id).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.IN_APP,
                    new ChannelChange(true, null, null, null, first.version())))
        .isInstanceOf(ConflictException.class);
    assertThat(settings.channel(tenant, NotificationChannel.IN_APP).enabled()).isFalse();
  }

  @Test
  void connectionRoundTripPreservesAllFieldsAndMasksReferences() throws Exception {
    var connection =
        new Connection(
            NotificationChannel.TELEGRAM,
            true,
            false,
            "provider/revision-1",
            "secrets/revision-1",
            null,
            "-100123",
            null,
            0);
    var saved = repository.saveConnection(tenant, connection, 0);
    assertThat(saved.connectionRef()).isEqualTo(connection.connectionRef());
    assertThat(saved.credentialsRef()).isEqualTo(connection.credentialsRef());
    assertThat(saved.chatId()).isEqualTo(connection.chatId());
    var entity = rows.findByTenantIdAndChannel(tenant, NotificationChannel.TELEGRAM).orElseThrow();
    assertThat(entity.getConnectionConfig()).doesNotContainKeys("type", "channel");
    var tele = json.convertValue(entity.getConnectionConfig(), TelegramConfig.class);
    assertThat(tele.chatId()).isEqualTo("-100123");
    assertThat(tele.url()).isEqualTo("provider/revision-1");
    assertThat(tele.token()).isEqualTo("secrets/revision-1");
    assertThat(repository.connection(tenant, NotificationChannel.TELEGRAM)).isEqualTo(saved);
    var response = settings.channel(tenant, NotificationChannel.TELEGRAM);
    assertThat(response.credentialsConfigured()).isTrue();
    assertThat(response.connectionVerified()).isFalse();
    assertThat(json.writeValueAsString(response))
        .doesNotContain(
            "credentialsRef", "connectionRef", "secrets/revision-1", "provider/revision-1");
    assertThat(repository.connection(UUID.randomUUID(), NotificationChannel.TELEGRAM).configured())
        .isFalse();
  }

  @Test
  void deletedConnectionRestoresSameKeyWithCurrentVersion() {
    settings.changeChannel(
        tenant, NotificationChannel.IN_APP, new ChannelChange(false, null, null, null, 0));
    var id =
        rows.findByTenantIdAndChannel(tenant, NotificationChannel.IN_APP).orElseThrow().getId();
    var entity = rows.findById(id).orElseThrow();
    entity.setDeletedAt(Instant.now());
    rows.saveAndFlush(entity);
    var deleted = repository.connection(tenant, NotificationChannel.IN_APP);
    assertThat(deleted.configured()).isFalse();
    assertThat(deleted.enabled()).isTrue();
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.IN_APP,
                    new ChannelChange(false, null, null, null, 0)))
        .isInstanceOf(ConflictException.class);
    var restored =
        settings.changeChannel(
            tenant,
            NotificationChannel.IN_APP,
            new ChannelChange(false, null, null, null, deleted.version()));
    assertThat(restored.version()).isEqualTo(deleted.version() + 1);
    assertThat(rows.findById(id).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void unverifiedProvidersCannotBeEnabledOrStoreTokens() {
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.TELEGRAM,
                    new ChannelChange(true, "secret", "-1001", null, 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(repository.connection(tenant, NotificationChannel.TELEGRAM).configured()).isFalse();
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.EMAIL,
                    new ChannelChange(true, null, null, null, 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(repository.connection(tenant, NotificationChannel.EMAIL).configured()).isFalse();
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.IN_APP,
                    new ChannelChange(true, "secret", null, null, 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void enumChannelSelectsConfigWithoutJsonType() {
    var email =
        new Connection(
            NotificationChannel.EMAIL, true, false, "smtp-conn", "sec/email", null, null, null, 0);
    var saved = repository.saveConnection(tenant, email, 0);
    var entity = rows.findByTenantIdAndChannel(tenant, NotificationChannel.EMAIL).orElseThrow();
    assertThat(entity.getConnectionConfig())
        .containsEntry("connectionRef", "smtp-conn")
        .containsEntry("credentialsRef", "sec/email")
        .doesNotContainKeys("type", "channel");
    assertThat(repository.connection(tenant, NotificationChannel.EMAIL)).isEqualTo(saved);

    settings.changeChannel(
        tenant, NotificationChannel.IN_APP, new ChannelChange(true, null, null, null, 0));
    assertThat(
            rows.findByTenantIdAndChannel(tenant, NotificationChannel.IN_APP)
                .orElseThrow()
                .getConnectionConfig())
        .isEmpty();
  }

  @Test
  void legacyJsonIsReadUsingStoredChannelInsteadOfEmbeddedType() {
    var connection =
        new Connection(NotificationChannel.TELEGRAM, true, false, null, null, null, null, null, 0);
    repository.saveConnection(tenant, connection, 0);
    var entity = rows.findByTenantIdAndChannel(tenant, NotificationChannel.TELEGRAM).orElseThrow();
    entity.setConnectionConfig(
        Map.of(
            "type", "EMAIL",
            "connectionRef", "legacy/provider",
            "credentialsRef", "legacy/secret",
            "telegramChatId", "-12345",
            "telegramBotUsername", "legacy_bot"));
    rows.saveAndFlush(entity);

    var loaded = repository.connection(tenant, NotificationChannel.TELEGRAM);
    assertThat(loaded.channel()).isEqualTo(NotificationChannel.TELEGRAM);
    assertThat(loaded.connectionRef()).isEqualTo("legacy/provider");
    assertThat(loaded.credentialsRef()).isEqualTo("legacy/secret");
    assertThat(loaded.chatId()).isEqualTo("-12345");
  }

  @Test
  void larkConnectionRoundTripUsesChannelAndPreservesExistingAppToken() {
    var connection =
        new Connection(
            NotificationChannel.LARK,
            true,
            false,
            "https://open.larksuite.com/hook/example",
            "secrets/lark",
            null,
            null,
            null,
            0);
    var saved = repository.saveConnection(tenant, connection, 0);
    var entity = rows.findByTenantIdAndChannel(tenant, NotificationChannel.LARK).orElseThrow();
    assertThat(entity.getConnectionConfig())
        .containsEntry("webhookUrl", connection.connectionRef())
        .containsEntry("appSecretRef", connection.credentialsRef())
        .doesNotContainKeys("type", "channel");
    assertThat(repository.connection(tenant, NotificationChannel.LARK)).isEqualTo(saved);

    var config = new HashMap<>(entity.getConnectionConfig());
    config.put("appToken", "secrets/lark-app-token");
    entity.setConnectionConfig(config);
    rows.saveAndFlush(entity);
    var current = repository.connection(tenant, NotificationChannel.LARK);
    repository.saveConnection(tenant, current, current.version());
    assertThat(
            rows.findByTenantIdAndChannel(tenant, NotificationChannel.LARK)
                .orElseThrow()
                .getConnectionConfig())
        .containsEntry("appToken", "secrets/lark-app-token");
    assertThatThrownBy(
            () ->
                settings.changeChannel(
                    tenant,
                    NotificationChannel.LARK,
                    new ChannelChange(true, null, null, null, current.version() + 1)))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void httpConfigurationRoundTripPersistsJsonAndEnforcesVersion() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new NotificationSettingController(settings))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    var actor =
        new GatewayPrincipal(
            UUID.randomUUID(),
            tenant,
            "USER",
            "MEMBER",
            Set.of("notifications.settings.view", "notifications.settings.manage"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, "unused", List.of()));
    try {
      String path = "/api/v1/notification-settings/channels/LARK";
      String body =
          """
          {"enabled":false,"version":0,"connectionConfig":{
            "webhookUrl":"https://open.larksuite.com/hook/private",
            "appSecretRef":"secrets/lark","appToken":"secrets/app-token"}}
          """;
      var saved =
          mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.version").value(1))
              .andExpect(jsonPath("$.credentialsConfigured").value(true))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(saved).doesNotContain("secrets/", "/hook/private");
      assertThat(
              rows.findByTenantIdAndChannel(tenant, NotificationChannel.LARK)
                  .orElseThrow()
                  .getConnectionConfig())
          .containsEntry("appToken", "secrets/app-token");
      mvc.perform(get(path))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.configured").value(true))
          .andExpect(jsonPath("$.connectionConfig.appToken").doesNotExist());
      mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isConflict());
      mvc.perform(
              put(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"enabled\":false,\"version\":1,\"connectionConfig\":{\"chatId\":\"-1\"}}"))
          .andExpect(status().isUnprocessableEntity());
      mvc.perform(get("/api/v1/notification-settings/channels"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(4));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void configureChannelStoresEachEnumShapeAndReturnsOnlyPublicConfig() throws Exception {
    var configurations =
        Map.of(
            NotificationChannel.IN_APP, Map.<String, String>of(),
            NotificationChannel.TELEGRAM,
                Map.of(
                    "url",
                    "https://api.telegram.org",
                    "token",
                    "secrets/telegram",
                    "chatId",
                    "-1001"),
            NotificationChannel.EMAIL,
                Map.of(
                    "connectionRef",
                    "smtp/connection",
                    "credentialsRef",
                    "secrets/email",
                    "fromAddress",
                    "sender@example.com",
                    "replyTo",
                    "reply@example.com"),
            NotificationChannel.LARK,
                Map.of(
                    "webhookUrl",
                    "https://open.larksuite.com/hook/private",
                    "appSecretRef",
                    "secrets/lark",
                    "appToken",
                    "secrets/lark-token"));
    for (var item : configurations.entrySet()) {
      var saved =
          settings.configureChannel(
              tenant, item.getKey(), new ConfigurationChange(false, item.getValue(), 0));
      assertThat(saved.version()).isEqualTo(1);
      assertThat(saved.configured()).isTrue();
      assertThat(saved.enabled()).isFalse();
      assertThat(saved.credentialsConfigured())
          .isEqualTo(item.getKey() != NotificationChannel.IN_APP);
      var entity = rows.findByTenantIdAndChannel(tenant, item.getKey()).orElseThrow();
      assertThat(entity.getConnectionConfig()).containsAllEntriesOf(item.getValue());
      assertThat(entity.getConnectionConfig()).doesNotContainKeys("type", "channel");
      var response = json.writeValueAsString(settings.channel(tenant, item.getKey()));
      assertThat(response)
          .doesNotContain("secrets/", "smtp/connection", "/hook/private", "api.telegram.org");
      assertThat(settings.channel(UUID.randomUUID(), item.getKey()).configured()).isFalse();
      assertThatThrownBy(
              () ->
                  settings.configureChannel(
                      tenant, item.getKey(), new ConfigurationChange(false, item.getValue(), 0)))
          .isInstanceOf(ConflictException.class);
      // The existing enabled/version-only API must retain all provider-specific fields.
      settings.changeChannel(tenant, item.getKey(), new ChannelChange(false, null, null, null, 1));
      assertThat(
              rows.findByTenantIdAndChannel(tenant, item.getKey())
                  .orElseThrow()
                  .getConnectionConfig())
          .containsAllEntriesOf(item.getValue());
    }
    assertThat(settings.channel(tenant, NotificationChannel.TELEGRAM).connectionConfig())
        .containsEntry("chatId", "-1001");
    assertThat(settings.channel(tenant, NotificationChannel.EMAIL).connectionConfig())
        .containsEntry("fromAddress", "sender@example.com")
        .containsEntry("replyTo", "reply@example.com");
  }

  @Test
  void configureChannelReplacesConfigAndRejectsInvalidOrUnverifiedChanges() {
    settings.configureChannel(
        tenant,
        NotificationChannel.LARK,
        new ConfigurationChange(false, Map.of("appToken", "secrets/old"), 0));
    settings.configureChannel(
        tenant, NotificationChannel.LARK, new ConfigurationChange(false, Map.of(), 1));
    assertThat(repository.connection(tenant, NotificationChannel.LARK).connectionConfig())
        .isEmpty();
    assertThat(settings.channel(tenant, NotificationChannel.LARK).credentialsConfigured())
        .isFalse();

    for (var invalid :
        List.of(
            Map.of("type", "TELEGRAM"), Map.of("chatId", "-1"),
            Map.of("webhookUrl", "http://example.com/hook"), Map.of("appToken", " "))) {
      assertThatThrownBy(
              () ->
                  settings.configureChannel(
                      tenant, NotificationChannel.LARK, new ConfigurationChange(false, invalid, 2)))
          .isInstanceOf(BusinessRuleViolationException.class);
    }
    assertThatThrownBy(
            () ->
                settings.configureChannel(
                    tenant,
                    NotificationChannel.EMAIL,
                    new ConfigurationChange(false, Map.of("fromAddress", "invalid-address"), 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(
            () ->
                settings.configureChannel(
                    tenant,
                    NotificationChannel.IN_APP,
                    new ConfigurationChange(true, Map.of("token", "secrets/not-allowed"), 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(
            () ->
                settings.configureChannel(
                    tenant,
                    NotificationChannel.LARK,
                    new ConfigurationChange(true, Map.of("appSecretRef", "secrets/ref"), 2)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(settings.channel(tenant, NotificationChannel.LARK).version()).isEqualTo(2);
  }

  @Test
  void concurrentCreateAndUpdateHaveOneWinnerPerVersion() throws Exception {
    assertSingleWinner(0);
    assertSingleWinner(1);
    assertThat(repository.connection(tenant, NotificationChannel.IN_APP).version()).isEqualTo(2);
  }

  private void assertSingleWinner(long version) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> update =
          () -> {
            start.await();
            try {
              settings.changeChannel(
                  tenant,
                  NotificationChannel.IN_APP,
                  new ChannelChange(false, null, null, null, version));
              return true;
            } catch (ConflictException ex) {
              return false;
            }
          };
      var first = pool.submit(update);
      var second = pool.submit(update);
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }
}
