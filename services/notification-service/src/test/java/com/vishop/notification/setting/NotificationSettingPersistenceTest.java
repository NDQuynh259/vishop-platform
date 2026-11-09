package com.vishop.notification.setting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.notification.composition.dispatch.NotificationDispatchPolicy;
import com.vishop.notification.composition.dispatch.NotificationSettingDispatchPolicy;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi;
import com.vishop.notification.modules.setting.application.api.NotificationAlertRuleApi.RuleChange;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi;
import com.vishop.notification.modules.setting.application.api.NotificationSettingManagementApi.*;
import com.vishop.notification.modules.setting.application.command.CreateChannelCommand;
import com.vishop.notification.modules.setting.application.command.CreateRuleCommand;
import com.vishop.notification.modules.setting.domain.entity.NotificationSettingState.Connection;
import com.vishop.notification.modules.setting.domain.port.NotificationSettingManagementRepository;
import com.vishop.notification.modules.setting.infrastructure.config.NotificationSettingModuleConfiguration;
import com.vishop.notification.modules.setting.infrastructure.persistence.adapters.NotificationSettingJpaAdapter;
import com.vishop.notification.modules.setting.infrastructure.persistence.entities.*;
import com.vishop.notification.modules.setting.infrastructure.persistence.model.*;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationAlertRuleRepository;
import com.vishop.notification.modules.setting.infrastructure.persistence.repositories.SpringDataNotificationChannelSettingRepository;
import com.vishop.notification.modules.setting.presentation.rest.controller.NotificationRuleController;
import com.vishop.notification.modules.setting.presentation.rest.controller.NotificationSettingController;
import com.vishop.notification.shared.constants.NotificationTypeCode;
import com.vishop.notification.shared.contract.NotificationChannel;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.BusinessRuleViolationException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
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
  @Autowired io.github.coreplatform.common.domain.cqrs.CommandBus commands;
  @Autowired io.github.coreplatform.common.domain.cqrs.QueryBus queries;
  @Autowired NotificationSettingManagementRepository repository;
  @Autowired SpringDataNotificationChannelSettingRepository rows;
  @Autowired ObjectMapper json;
  @Autowired jakarta.validation.Validator validator;
  @Autowired NotificationAlertRuleApi rules;
  @Autowired SpringDataNotificationAlertRuleRepository ruleRows;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  final UUID tenant = UUID.randomUUID();

  @Test
  void ruleHttpLifecycleIsTenantScopedVersionedAndStoresAllChannels() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new NotificationRuleController(commands, queries))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    var actor =
        new GatewayPrincipal(
            UUID.randomUUID(),
            tenant,
            "USER",
            "OWNER",
            Set.of("notifications.settings.view", "notifications.settings.manage"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    try {
      String path = "/api/v1/notifications/settings/rules";
      var response =
          mvc.perform(
                  post(path)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[\"IN_APP\",\"EMAIL\",\"TELEGRAM\",\"LARK\"]}"))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.version").value(1))
              .andReturn()
              .getResponse();
      UUID id = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
      assertThat(id.version()).isEqualTo(7);
      var stored = ruleRows.findById(id).orElseThrow();
      assertThat(stored.getTenantId()).isEqualTo(tenant);
      assertThat(stored.getChannels()).contains("LARK");
      jdbc.update(
          "UPDATE notification_alert_rules SET thresholds=?::jsonb,cooldown_seconds=? WHERE id=?",
          "{\"legacyLimit\":10}",
          172800,
          id);
      assertThat(rules.ruleById(tenant, id).getChannels())
          .containsExactlyInAnyOrder(NotificationChannel.values());
      mvc.perform(
              post(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"type\":\"STOCK_LOW\",\"enabled\":false,\"channels\":[]}"))
          .andExpect(status().isConflict());
      String location = response.getHeader("Location");
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()));
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false,\"channels\":[\"EMAIL\"],\"version\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(2));
      assertThat(rules.ruleById(tenant, id).getChannels())
          .containsExactly(NotificationChannel.EMAIL);
      assertThat(
              jdbc.queryForObject(
                  "SELECT cooldown_seconds FROM notification_alert_rules WHERE id=?",
                  Integer.class,
                  id))
          .isEqualTo(172800);
      assertThat(
              jdbc.queryForObject(
                  "SELECT thresholds->>'legacyLimit' FROM notification_alert_rules WHERE id=?",
                  String.class,
                  id))
          .isEqualTo("10");
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":true,\"channels\":[],\"version\":1}"))
          .andExpect(status().isConflict());
      var foreign =
          rules.createRule(
              UUID.randomUUID(),
              NotificationTypeCode.STOCK_LOW,
              true,
              Set.of(NotificationChannel.IN_APP));
      for (UUID missing : List.of(UUID.randomUUID(), foreign.getId())) {
        mvc.perform(get(path + "/" + missing)).andExpect(status().isNotFound());
        mvc.perform(
                put(path + "/" + missing)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false,\"channels\":[],\"version\":1}"))
            .andExpect(status().isNotFound());
      }
      var row = ruleRows.findById(id).orElseThrow();
      row.setDeletedAt(Instant.now());
      ruleRows.saveAndFlush(row);
      mvc.perform(get(location)).andExpect(status().isNotFound());
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false,\"channels\":[],\"version\":3}"))
          .andExpect(status().isNotFound());
      mvc.perform(
              post(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"type\":\"STOCK_LOW\",\"enabled\":true,\"channels\":[]}"))
          .andExpect(status().isConflict());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void rulesAndConnectionSettingsDetermineRoutesWithoutOtherPreferenceTables() {
    var code = NotificationTypeCode.STOCK_LOW;
    var type =
        new NotificationDispatchPolicy.TypePolicy(
            code.code(),
            code.feature().code(),
            true,
            code.defaultEnabled(),
            code.defaultChannels());
    var policy = new NotificationSettingDispatchPolicy(settings, rules);
    assertThat(rules.rules(tenant)).hasSize(NotificationTypeCode.values().length);
    assertThat(rules.ruleForType(tenant, code).getId()).isNull();
    assertThat(policy.resolve(tenant, type).channels()).containsExactly(NotificationChannel.IN_APP);
    var rule =
        rules.createRule(
            tenant, code, true, Set.of(NotificationChannel.IN_APP, NotificationChannel.LARK));
    assertThat(policy.resolve(tenant, type).channels()).containsExactly(NotificationChannel.IN_APP);
    var channel = settings.createChannel(tenant, NotificationChannel.IN_APP, null, false, Map.of());
    assertThat(policy.resolve(tenant, type).channels()).isEmpty();
    settings.updateChannel(
        tenant,
        channel.id(),
        new ChannelUpdate(true, (Map<String, String>) null, channel.version()));
    assertThat(policy.resolve(tenant, type).channels()).containsExactly(NotificationChannel.IN_APP);
    rules.updateRule(
        tenant, rule.getId(), new RuleChange(false, rule.getChannels(), rule.getVersion()));
    assertThat(policy.resolve(tenant, type).channels()).isEmpty();
    assertThat(
            policy.restrictForUser(
                tenant, UUID.randomUUID(), code.code(), Set.of(NotificationChannel.IN_APP)))
        .containsExactly(NotificationChannel.IN_APP);
  }

  @Test
  void concurrentRuleCreatesAndUpdatesHaveOneWinner() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> create =
          () -> {
            start.await();
            try {
              commands.dispatch(
                  new CreateRuleCommand(
                      tenant,
                      NotificationTypeCode.STOCK_LOW,
                      true,
                      Set.of(NotificationChannel.LARK)));
              return true;
            } catch (ConflictException ex) {
              return false;
            }
          };
      var one = pool.submit(create);
      var two = pool.submit(create);
      start.countDown();
      assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    var rule = rules.ruleForType(tenant, NotificationTypeCode.STOCK_LOW);
    var updates = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> update =
          () -> {
            updates.await();
            try {
              rules.updateRule(
                  tenant, rule.getId(), new RuleChange(false, Set.of(), rule.getVersion()));
              return true;
            } catch (ConflictException ex) {
              return false;
            }
          };
      var one = pool.submit(update);
      var two = pool.submit(update);
      updates.countDown();
      assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(rules.ruleById(tenant, rule.getId()).getVersion()).isEqualTo(rule.getVersion() + 1);
  }

  private UUID channelId(NotificationChannel channel) {
    return rows.findByTenantIdAndChannel(tenant, channel)
        .map(NotificationChannelSettingJpaEntity::getId)
        .orElseGet(
            () -> {
              var entity = new NotificationChannelSettingJpaEntity();
              entity.setId(UuidV7.nextId());
              entity.setTenantId(tenant);
              entity.setChannel(channel);
              entity.setEnabled(false);
              return rows.saveAndFlush(entity).getId();
            });
  }

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
        settings.updateChannel(
            tenant,
            channelId(NotificationChannel.IN_APP),
            new ChannelUpdate(false, null, null, null, 0));
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
        settings.updateChannel(
            tenant,
            channelId(NotificationChannel.IN_APP),
            new ChannelUpdate(false, null, null, null, first.version()));
    assertThat(next.version()).isEqualTo(2);
    assertThat(rows.findById(id).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.IN_APP),
                    new ChannelUpdate(true, null, null, null, first.version())))
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
  void updateDoesNotRestoreDeletedRecords() {
    UUID id = channelId(NotificationChannel.IN_APP);
    var entity = rows.findById(id).orElseThrow();
    entity.setDeletedAt(Instant.now());
    entity = rows.saveAndFlush(entity);
    var version = entity.getVersion();
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant, id, new ChannelUpdate(false, null, null, null, version)))
        .isInstanceOf(EntityNotFoundException.class);
    assertThat(rows.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    assertThat(rows.findById(id).orElseThrow().getVersion()).isEqualTo(version);
  }

  @Test
  void unverifiedProvidersCannotBeEnabledOrStoreTokens() {
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.TELEGRAM),
                    new ChannelUpdate(true, "secret", "-1001", null, 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(repository.connection(tenant, NotificationChannel.TELEGRAM).version()).isZero();
    assertThat(repository.connection(tenant, NotificationChannel.TELEGRAM).connectionConfig())
        .isEmpty();
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.EMAIL),
                    new ChannelUpdate(true, null, null, null, 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(repository.connection(tenant, NotificationChannel.EMAIL).version()).isZero();
    assertThat(repository.connection(tenant, NotificationChannel.EMAIL).connectionConfig())
        .isEmpty();
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.IN_APP),
                    new ChannelUpdate(true, "secret", null, null, 0)))
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

    settings.updateChannel(
        tenant,
        channelId(NotificationChannel.IN_APP),
        new ChannelUpdate(true, null, null, null, 0));
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
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.LARK),
                    new ChannelUpdate(true, null, null, null, current.version() + 1)))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void httpConfigurationRoundTripPersistsJsonAndEnforcesVersion() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationSettingController(commands, queries, json, validator))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
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
      var initial =
          settings.updateChannel(
              tenant,
              channelId(NotificationChannel.LARK),
              new NotificationSettingManagementApi.ChannelUpdate(false, Map.of(), 0));
      String path = "/api/v1/notifications/settings/channels/" + initial.id();
      String body =
          """
          {"enabled":false,"version":1,"connectionConfig":{
            "webhookUrl":"https://open.larksuite.com/hook/private",
            "appSecretRef":"secrets/lark","appToken":"secrets/app-token"}}
          """;
      var saved =
          mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.version").value(2))
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
      mvc.perform(get("/api/v1/notifications/settings/channels"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(4))
          .andExpect(jsonPath("$.pageInfo.total").value(4));
      mvc.perform(
              get("/api/v1/notifications/settings/channels").param("page", "2").param("limit", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.length()").value(1))
          .andExpect(jsonPath("$.pageInfo.page").value(2))
          .andExpect(jsonPath("$.pageInfo.total").value(4));
      String cursor = null;
      var channelNames = new java.util.HashSet<String>();
      for (int i = 0; i < 4; i++) {
        var request =
            get("/api/v1/notifications/settings/channels")
                .param("pageMode", "cursor")
                .param("limit", "1");
        if (cursor != null) request.param("cursor", cursor);
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var cursorBody = json.readTree(response.getResponse().getContentAsString());
        assertThat(channelNames.add(cursorBody.path("data").get(0).path("channel").asText()))
            .isTrue();
        assertThat(cursorBody.path("pageInfo").has("total")).isFalse();
        cursor =
            cursorBody.path("pageInfo").path("nextCursor").isTextual()
                ? cursorBody.path("pageInfo").path("nextCursor").asText()
                : null;
      }
      assertThat(cursor).isNull();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void postCreatesIndependentLarkRecordsAndSupportsGetPutByReturnedId() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationSettingController(commands, queries, json, validator))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    var actor =
        new GatewayPrincipal(
            UUID.randomUUID(),
            tenant,
            "USER",
            "OWNER",
            Set.of("notifications.settings.view", "notifications.settings.manage"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, "unused", List.of()));
    try {
      String body =
          "{\"channel\":\"LARK\",\"name\":\"Lark"
              + " alerts\",\"enabled\":false,\"connectionConfig\":{\"appToken\":\"secret-ref\"}}";
      var response =
          mvc.perform(
                  post("/api/v1/notifications/settings/channels")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.version").value(1))
              .andExpect(jsonPath("$.name").value("Lark alerts"))
              .andExpect(jsonPath("$.connectionConfig.appToken").doesNotExist())
              .andReturn()
              .getResponse();
      UUID id = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
      assertThat(rows.findById(id).orElseThrow().getTenantId()).isEqualTo(tenant);
      assertThat(rows.findById(id).orElseThrow().getConnectionConfig())
          .containsEntry("appToken", "secret-ref");
      mvc.perform(
              post("/api/v1/notifications/settings/channels")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"channel\":\"LARK\",\"enabled\":false,\"connectionConfig\":{\"appToken\":\"replacement\"}}"))
          .andExpect(status().isCreated());
      assertThat(rows.findById(id).orElseThrow().getConnectionConfig())
          .containsEntry("appToken", "secret-ref");
      String location = response.getHeader("Location");
      mvc.perform(get(location))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()));
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"name\":\"Updated"
                          + " alerts\",\"enabled\":false,\"version\":1,\"connectionConfig\":{}}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(2));
      assertThat(rows.findById(id).orElseThrow().getConnectionConfig().get("appToken")).isNull();
      assertThat(rows.findById(id).orElseThrow().getName()).isEqualTo("Updated alerts");
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"  Renamed alerts  \",\"enabled\":false,\"version\":2}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Renamed alerts"));
      mvc.perform(
              put(location)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false,\"version\":3}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Renamed alerts"));
      assertThat(settings.channelById(tenant, id).name()).isEqualTo("Renamed alerts");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void httpRecordIdsAreTenantScopedAndDeletedRecordsAreNotAddressable() throws Exception {
    var own =
        settings.updateChannel(
            tenant,
            channelId(NotificationChannel.LARK),
            new NotificationSettingManagementApi.ChannelUpdate(false, Map.of(), 0));
    var foreign =
        settings.createChannel(UUID.randomUUID(), NotificationChannel.LARK, null, false, Map.of());
    var deleted =
        settings.updateChannel(
            tenant,
            channelId(NotificationChannel.EMAIL),
            new NotificationSettingManagementApi.ChannelUpdate(false, Map.of(), 0));
    var deletedRow = rows.findById(deleted.id()).orElseThrow();
    deletedRow.setDeletedAt(Instant.now());
    rows.saveAndFlush(deletedRow);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationSettingController(commands, queries, json, validator))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
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
      for (UUID id : List.of(UUID.randomUUID(), foreign.id(), deleted.id())) {
        String path = "/api/v1/notifications/settings/channels/" + id;
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(
                put(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false,\"version\":1}"))
            .andExpect(status().isNotFound());
        mvc.perform(
                put(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false,\"version\":1,\"connectionConfig\":{}}"))
            .andExpect(status().isNotFound());
      }
      String path = "/api/v1/notifications/settings/channels/" + own.id();
      mvc.perform(get(path))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(own.id().toString()))
          .andExpect(jsonPath("$.channel").value("LARK"));
      mvc.perform(
              put(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false,\"version\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(own.id().toString()))
          .andExpect(jsonPath("$.version").value(2));
      assertThat(settings.channelById(tenant, own.id()).version()).isEqualTo(2);
      assertThat(rows.findById(foreign.id()).orElseThrow().getVersion()).isEqualTo(1);
      assertThat(rows.findById(deleted.id()).orElseThrow().getDeletedAt()).isNotNull();
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
          settings.updateChannel(
              tenant, channelId(item.getKey()), new ChannelUpdate(false, item.getValue(), 0));
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
                  settings.updateChannel(
                      tenant,
                      channelId(item.getKey()),
                      new ChannelUpdate(false, item.getValue(), 0)))
          .isInstanceOf(ConflictException.class);
      // The existing enabled/version-only API must retain all provider-specific fields.
      settings.updateChannel(
          tenant, channelId(item.getKey()), new ChannelUpdate(false, null, null, null, 1));
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
    settings.updateChannel(
        tenant,
        channelId(NotificationChannel.LARK),
        new ChannelUpdate(false, Map.of("appToken", "secrets/old"), 0));
    settings.updateChannel(
        tenant, channelId(NotificationChannel.LARK), new ChannelUpdate(false, Map.of(), 1));
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
                  settings.updateChannel(
                      tenant,
                      channelId(NotificationChannel.LARK),
                      new ChannelUpdate(false, invalid, 2)))
          .isInstanceOf(BusinessRuleViolationException.class);
    }
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.EMAIL),
                    new ChannelUpdate(false, Map.of("fromAddress", "invalid-address"), 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.IN_APP),
                    new ChannelUpdate(true, Map.of("token", "secrets/not-allowed"), 0)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    channelId(NotificationChannel.LARK),
                    new ChannelUpdate(true, Map.of("appSecretRef", "secrets/ref"), 2)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(settings.channel(tenant, NotificationChannel.LARK).version()).isEqualTo(2);
  }

  @Test
  void createCommandHasOneWinnerAndDoesNotRestoreDeletedRows() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> create =
          () -> {
            start.await();
            try {
              commands.dispatch(
                  new CreateChannelCommand(
                      tenant, NotificationChannel.IN_APP, null, true, Map.of()));
              return true;
            } catch (ConflictException ex) {
              return false;
            }
          };
      var first = pool.submit(create);
      var second = pool.submit(create);
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    var stored = rows.findByTenantIdAndChannel(tenant, NotificationChannel.IN_APP).orElseThrow();
    stored.setDeletedAt(Instant.now());
    rows.saveAndFlush(stored);
    assertThatThrownBy(
            () -> settings.createChannel(tenant, NotificationChannel.IN_APP, null, true, Map.of()))
        .isInstanceOf(ConflictException.class);
    assertThat(rows.findById(stored.getId()).orElseThrow().getDeletedAt()).isNotNull();
    assertThat(
            settings
                .createChannel(UUID.randomUUID(), NotificationChannel.IN_APP, null, true, Map.of())
                .id())
        .isNotEqualTo(stored.getId());
    assertThatThrownBy(
            () -> settings.createChannel(tenant, NotificationChannel.LARK, null, true, Map.of()))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(rows.findByTenantIdAndChannel(tenant, NotificationChannel.LARK)).isEmpty();
  }

  @Test
  void multipleLarkGroupsAreListedAndUpdatedByIdWithoutAmbiguousChannelLookup() throws Exception {
    var first =
        settings.createChannel(
            tenant,
            NotificationChannel.LARK,
            "Operations",
            false,
            Map.of("webhookUrl", "https://open.larksuite.com/hook/operations"));
    var second =
        settings.createChannel(
            tenant,
            NotificationChannel.LARK,
            "Accounting",
            false,
            Map.of("webhookUrl", "https://open.larksuite.com/hook/accounting"));
    assertThat(first.id()).isNotEqualTo(second.id());
    assertThat(settings.channels(tenant)).hasSize(5);
    assertThat(
            settings.channels(tenant).stream().filter(c -> c.channel() == NotificationChannel.LARK))
        .extracting(ChannelView::id)
        .containsExactly(first.id(), second.id());
    assertThatThrownBy(() -> settings.channel(tenant, NotificationChannel.LARK))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                repository.saveConnection(
                    tenant,
                    new Connection(
                        NotificationChannel.LARK, true, false, null, null, null, null, null, 0),
                    0))
        .isInstanceOf(ConflictException.class);
    settings.updateChannel(
        tenant,
        first.id(),
        new ChannelUpdate(
            false, Map.of("webhookUrl", "https://open.larksuite.com/hook/operations-updated"), 1));
    assertThat(rows.findById(first.id()).orElseThrow().getConnectionConfig())
        .containsEntry("webhookUrl", "https://open.larksuite.com/hook/operations-updated");
    assertThat(rows.findById(second.id()).orElseThrow().getConnectionConfig())
        .containsEntry("webhookUrl", "https://open.larksuite.com/hook/accounting");
    assertThatThrownBy(() -> settings.channelById(UUID.randomUUID(), first.id()))
        .isInstanceOf(EntityNotFoundException.class);

    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationSettingController(commands, queries, json, validator))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new io.github.coreplatform.common.web.pagination
                    .PaginationRequestArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    var actor =
        new GatewayPrincipal(
            UUID.randomUUID(), tenant, "USER", "OWNER", Set.of("notifications.settings.view"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    try {
      mvc.perform(
              get("/api/v1/notifications/settings/channels").param("page", "2").param("limit", "3"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pageInfo.total").value(5))
          .andExpect(jsonPath("$.data.length()").value(2));
      String cursor = null;
      var keys = new HashSet<String>();
      var groupIds = new HashSet<String>();
      for (int page = 0; page < 5; page++) {
        var request =
            get("/api/v1/notifications/settings/channels")
                .param("pageMode", "cursor")
                .param("limit", "1");
        if (cursor != null) request.param("cursor", cursor);
        var response =
            mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).doesNotContain("/hook/operations", "/hook/accounting");
        var body = json.readTree(response);
        var item = body.path("data").get(0);
        assertThat(keys.add(item.path("channel").asText() + ":" + item.path("id").asText()))
            .isTrue();
        if (item.path("channel").asText().equals("LARK")) groupIds.add(item.path("id").asText());
        cursor = body.path("pageInfo").path("nextCursor").asText(null);
      }
      assertThat(cursor).isNull();
      assertThat(groupIds).containsExactlyInAnyOrder(first.id().toString(), second.id().toString());
    } finally {
      SecurityContextHolder.clearContext();
    }
    var deleted = rows.findById(first.id()).orElseThrow();
    deleted.setDeletedAt(Instant.now());
    rows.saveAndFlush(deleted);
    assertThat(
            settings.channels(tenant).stream().filter(c -> c.channel() == NotificationChannel.LARK))
        .extracting(ChannelView::id)
        .containsExactly(second.id());
  }

  @Test
  void unifiedUpdatePreservesConfigWhenOmittedAndRejectsMixedInputsWithoutWriting() {
    var created =
        settings.createChannel(
            tenant,
            NotificationChannel.TELEGRAM,
            "Alerts",
            false,
            Map.of("token", "secrets/old", "chatId", "-1001"));
    var replaced =
        settings.updateChannel(
            tenant,
            created.id(),
            new ChannelUpdate(
                false,
                Map.of("token", "secrets/new", "chatId", "-1002"),
                created.version(),
                "Renamed",
                null,
                null,
                null));
    var toggled =
        settings.updateChannel(
            tenant,
            created.id(),
            new ChannelUpdate(false, (Map<String, String>) null, replaced.version()));
    assertThat(toggled.id()).isEqualTo(created.id());
    assertThat(toggled.name()).isEqualTo("Renamed");
    assertThat(repository.connectionById(tenant, created.id()).connectionConfig())
        .containsEntry("token", "secrets/new")
        .containsEntry("chatId", "-1002");
    assertThatThrownBy(
            () ->
                settings.updateChannel(
                    tenant,
                    created.id(),
                    new ChannelUpdate(
                        false, Map.of(), toggled.version(), null, "raw-token", null, null)))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThat(settings.channelById(tenant, created.id()).version()).isEqualTo(toggled.version());
    assertThat(repository.connectionById(tenant, created.id()).connectionConfig())
        .containsEntry("token", "secrets/new");
  }

  @Test
  void concurrentUpdatesHaveOneWinnerPerVersion() throws Exception {
    assertSingleWinner(0);
    assertSingleWinner(1);
    assertThat(repository.connection(tenant, NotificationChannel.IN_APP).version()).isEqualTo(2);
  }

  private void assertSingleWinner(long version) throws Exception {
    UUID id = channelId(NotificationChannel.IN_APP);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> update =
          () -> {
            start.await();
            try {
              settings.updateChannel(
                  tenant, id, new ChannelUpdate(false, null, null, null, version));
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
