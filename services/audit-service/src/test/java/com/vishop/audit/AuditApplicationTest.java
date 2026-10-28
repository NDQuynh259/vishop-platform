package com.vishop.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.audit.modules.auditlog.application.command.RecordAuditLogCommand;
import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.dto.CmsAuditActor;
import com.vishop.audit.modules.auditlog.application.query.ListAuditLogsQuery;
import com.vishop.audit.modules.auditlog.application.query.ListCmsAuditLogsQuery;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories.SpringDataAuditLogRepository;
import com.vishop.audit.modules.auditlog.presentation.messaging.AuditKafkaListener;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.events.EventEnvelope;
import io.github.coreplatform.common.persistence.OutboxEventRepository;
import io.github.coreplatform.common.persistence.OutboxPublisher;
import io.github.coreplatform.common.persistence.OutboxService;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageRepository;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "management.tracing.enabled=false",
      "vishop.security.jwt.secret=test-audit-jwt-secret-with-at-least-thirty-two-bytes"
    })
@DirtiesContext
class AuditApplicationTest {
  private static final EmbeddedPostgres POSTGRES = startPostgres();

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

  @Autowired ApplicationContext context;
  @Autowired TestRestTemplate http;
  @Autowired KafkaListenerEndpointRegistry listeners;

  @Autowired QueryBus queries;
  @Autowired CommandBus commands;
  @Autowired ObjectMapper json;
  @Autowired ConsumedMessageRepository consumed;

  @Test
  void kafkaCommandPersistsOnceAndQueriesRemainTenantScoped() throws Exception {
    var tenant = UUID.randomUUID();
    var otherTenant = UUID.randomUUID();
    var aggregate = UUID.randomUUID();
    var event =
        EventEnvelope.create(
            "TenantRenamed",
            "iam-service",
            tenant,
            "Tenant",
            aggregate,
            null,
            null,
            json.readTree("{\"name\":\"Shop\"}"));
    var record =
        new ConsumerRecord<String, String>(
            "vishop.tenant.events", 0, 0L, aggregate.toString(), json.writeValueAsString(event));
    var listener = context.getBean(AuditKafkaListener.class);
    listener.onEvent(record);
    listener.onEvent(record);
    commands.dispatch(
        new RecordAuditLogCommand(
            UUID.randomUUID(),
            "TenantRenamed",
            otherTenant,
            "Tenant",
            aggregate,
            "iam-service",
            "{}",
            Instant.now()));
    List<AuditLogView> result =
        queries.dispatch(new ListAuditLogsQuery(tenant, "Tenant", aggregate, 0, 10));
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().eventId()).isEqualTo(event.eventId());
    List<AuditLogView> otherAggregate =
        queries.dispatch(new ListAuditLogsQuery(tenant, "Tenant", UUID.randomUUID(), 0, 10));
    assertThat(otherAggregate).isEmpty();
    List<AuditLogView> nextPage =
        queries.dispatch(new ListAuditLogsQuery(tenant, null, null, 1, 10));
    assertThat(nextPage).isEmpty();

    assertThat(json.readTree(result.getFirst().payload()).path("name").asText()).isEqualTo("Shop");
    assertThat(
            consumed.existsByMessageIdAndConsumerGroup(event.eventId().toString(), "audit-service"))
        .isTrue();
  }

  @Test
  void failedPersistenceRollsBackConsumptionAndAllowsRetry() {
    var eventId = UUID.randomUUID();
    var tenant = UUID.randomUUID();
    var aggregate = UUID.randomUUID();
    // Invalid JSON passes through the domain as opaque data, but PostgreSQL rejects it.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                commands.dispatch(
                    new RecordAuditLogCommand(
                        eventId,
                        "TenantRenamed",
                        tenant,
                        "Tenant",
                        aggregate,
                        "iam-service",
                        "invalid-json",
                        Instant.now())))
        .isInstanceOf(RuntimeException.class);
    assertThat(consumed.existsByMessageIdAndConsumerGroup(eventId.toString(), "audit-service"))
        .isFalse();
    commands.dispatch(
        new RecordAuditLogCommand(
            eventId,
            "TenantRenamed",
            tenant,
            "Tenant",
            aggregate,
            "iam-service",
            "{}",
            Instant.now()));
    assertThat(consumed.existsByMessageIdAndConsumerGroup(eventId.toString(), "audit-service"))
        .isTrue();
  }

  @Autowired JwtTokenProvider tokens;

  private ResponseEntity<JsonNode> cmsGet(String path, String token) {
    var headers = new HttpHeaders();
    if (token != null) headers.setBearerAuth(token);
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  private String cmsToken() {
    return tokens.generateIamAccessToken(
        UUID.randomUUID(), null, SystemRole.PLATFORM_ADMIN.name(), null, java.util.Set.of());
  }

  @Test
  void cmsListsGlobalAndTenantEventsWithFiltersAndPagination() {
    var eventType = "CmsTest-" + UUID.randomUUID();
    var tenant = UUID.randomUUID();
    var other = UUID.randomUUID();
    var time = Instant.parse("2026-10-01T00:00:00Z");
    for (int index = 0; index < 3; index++) {
      UUID eventTenant = index == 0 ? null : index == 1 ? tenant : other;
      commands.dispatch(
          new RecordAuditLogCommand(
              UUID.randomUUID(),
              eventType,
              eventTenant,
              "Tenant",
              eventTenant == null ? UUID.randomUUID() : eventTenant,
              "iam-service",
              "{\"actorId\":\"admin\"}",
              time.plusSeconds(index)));
    }
    var token = cmsToken();
    String base = "/api/v1/cms/audit-logs?eventType=" + eventType;
    var first = cmsGet(base + "&limit=1", token);
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    var body = first.getBody();
    assertThat(body.path("object").asText()).isEqualTo("list");
    assertThat(body.path("data").get(0).path("createdAt").isTextual()).isTrue();
    assertThat(body.path("data").get(0).has("recordedAt")).isFalse();
    assertThat(body.path("pageInfo").path("total").asLong()).isEqualTo(3);
    assertThat(body.path("pageInfo").path("hasNextPage").asBoolean()).isTrue();
    assertThat(body.path("data").get(0).path("tenantId").asText()).isEqualTo(other.toString());
    assertThat(body.path("data").get(0).path("payload").path("actorId").asText())
        .isEqualTo("admin");
    var second = cmsGet(base + "&limit=1&page=2", token).getBody();
    assertThat(second.path("data").get(0).path("tenantId").asText()).isEqualTo(tenant.toString());
    var global = cmsGet(base + "&limit=1&page=3", token).getBody();
    assertThat(global.path("data").get(0).path("tenantId").isNull()).isTrue();
    assertThat(global.path("pageInfo").path("hasNextPage").asBoolean()).isFalse();
    var filtered =
        cmsGet(
                base
                    + "&tenantId="
                    + tenant
                    + "&aggregateType=Tenant&aggregateId="
                    + tenant
                    + "&from=2026-10-01T00:00:01Z&to=2026-10-01T00:00:02Z",
                token)
            .getBody();
    assertThat(filtered.path("pageInfo").path("total").asLong()).isEqualTo(1);
    var empty = cmsGet(base + "&tenantId=" + UUID.randomUUID(), token).getBody();
    assertThat(empty.path("data").size()).isZero();
    assertThat(empty.path("pageInfo").path("total").asLong()).isZero();
    assertThat(cmsGet(base + "&from=2026-10-01T00:00:03Z", token).getBody().path("data").size())
        .isZero();
  }

  @Test
  void cmsRejectsUnauthenticatedAndTenantRolesWithoutSystemAdmin() {
    assertThat(cmsGet("/api/v1/cms/audit-logs", null).getStatusCode().value()).isEqualTo(401);
    assertThat(cmsGet("/api/v1/cms/audit-logs", "invalid-token").getStatusCode().value())
        .isEqualTo(401);
    for (String role : List.of("OWNER", SystemRole.PLATFORM_ADMIN.name())) {
      var token =
          tokens.generateIamAccessToken(
              UUID.randomUUID(),
              UUID.randomUUID(),
              SystemRole.USER.name(),
              role,
              java.util.Set.of("audit_log.view"));
      assertThat(cmsGet("/api/v1/cms/audit-logs", token).getStatusCode().value()).isEqualTo(403);
    }
    var refresh =
        tokens.generateRefreshToken(UUID.randomUUID(), null, Instant.now().plusSeconds(600));
    assertThat(cmsGet("/api/v1/cms/audit-logs", refresh).getStatusCode().value()).isEqualTo(401);
    var headers = new HttpHeaders();
    headers.set("X-User-Id", UUID.randomUUID().toString());
    headers.set("X-User-Roles", SystemRole.PLATFORM_ADMIN.name());
    assertThat(
            http.exchange(
                    "/api/v1/cms/audit-logs",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                queries.dispatch(
                    new ListCmsAuditLogsQuery(
                        new CmsAuditActor(UUID.randomUUID(), SystemRole.USER.name()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetPagination.ofDefault())))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void cmsRejectsInvalidFiltersAndPagination() {
    var token = cmsToken();
    for (String query :
        List.of(
            "page=invalid",
            "limit=invalid",
            "cursor=unsupported",
            "tenantId=invalid",
            "aggregateType=Tenant",
            "aggregateId=" + UUID.randomUUID(),
            "eventType=",
            "from=invalid",
            "from=2026-10-02T00:00:00Z&to=2026-10-01T00:00:00Z")) {
      assertThat(cmsGet("/api/v1/cms/audit-logs?" + query, token).getStatusCode().value())
          .as(query)
          .isEqualTo(400);
    }
  }

  @Test
  void cmsUsesSharedPaginationDefaultsAndNormalization() {
    var token = cmsToken();
    var defaults = cmsGet("/api/v1/cms/audit-logs", token).getBody().path("pageInfo");
    assertThat(defaults.path("page").asInt()).isEqualTo(1);
    assertThat(defaults.path("limit").asInt()).isEqualTo(20);
    var normalized =
        cmsGet("/api/v1/cms/audit-logs?page=0&limit=101", token).getBody().path("pageInfo");
    assertThat(normalized.path("page").asInt()).isEqualTo(1);
    assertThat(normalized.path("limit").asInt()).isEqualTo(100);
    var nonpositive =
        cmsGet("/api/v1/cms/audit-logs?page=-2&limit=0", token).getBody().path("pageInfo");
    assertThat(nonpositive.path("page").asInt()).isEqualTo(1);
    assertThat(nonpositive.path("limit").asInt()).isEqualTo(20);
  }

  @Test
  void exposesOpenApiSpecificationWithoutAuthentication() {
    var response = http.getForEntity("/v3/api-docs", JsonNode.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().path("openapi").asText()).startsWith("3.");
    assertThat(response.getBody().path("info").path("title").asText())
        .isEqualTo("ViShop Platform REST API");
  }

  @Test
  void startsHttpAndKafkaInfrastructureWithoutImportingOutboxComponents() {
    assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
    assertThat(context.getBean(SpringDataAuditLogRepository.class)).isNotNull();
    assertThat(context.getBean(ConsumedMessageRepository.class)).isNotNull();
    assertThat(context.getBean(AuditKafkaListener.class)).isNotNull();
    assertThat(context.getBean(CommonErrorHandler.class)).isNotNull();
    assertThat(context.getBeansOfType(OutboxPublisher.class)).isEmpty();
    assertThat(context.getBeansOfType(OutboxService.class)).isEmpty();
    assertThat(context.getBeansOfType(OutboxEventRepository.class)).isEmpty();
    assertThat(listeners.getListenerContainers())
        .hasSize(1)
        .allSatisfy(container -> assertThat(container.isRunning()).isFalse());
    var response = http.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }
}
