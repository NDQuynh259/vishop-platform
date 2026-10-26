package com.vishop.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vishop.audit.domain.AuditLogRepository;
import com.vishop.audit.infrastructure.kafka.AuditKafkaListener;
import io.github.coreplatform.common.persistence.OutboxEventRepository;
import io.github.coreplatform.common.persistence.OutboxPublisher;
import io.github.coreplatform.common.persistence.OutboxService;
import io.github.coreplatform.common.persistence.idempotency.ConsumedMessageRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
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

  @Autowired ApplicationContext context;
  @Autowired TestRestTemplate http;
  @Autowired KafkaListenerEndpointRegistry listeners;

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
    assertThat(context.getBean(AuditLogRepository.class)).isNotNull();
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
