package com.vishop.audit.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.audit.domain.AuditLogEntity;
import com.vishop.audit.domain.AuditLogRepository;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.events.EventEnvelope;
import io.github.coreplatform.common.persistence.idempotency.IdempotentConsumer;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka event listener that captures events across the platform and writes audit trail logs.
 * Employs {@link IdempotentConsumer} to guarantee deduplication.
 */
@Component
public class AuditKafkaListener {

  private static final Logger log = LoggerFactory.getLogger(AuditKafkaListener.class);
  private static final String CONSUMER_GROUP = "audit-service";

  private final AuditLogRepository auditLogRepository;
  private final IdempotentConsumer idempotentConsumer;
  private final ObjectMapper objectMapper;

  // #region AuditKafkaListener
  public AuditKafkaListener(
      AuditLogRepository auditLogRepository,
      IdempotentConsumer idempotentConsumer,
      ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.idempotentConsumer = idempotentConsumer;
    this.objectMapper = objectMapper;
  }

  // #endregion

  // #region onEvent
  @KafkaListener(
      topics = {"vishop.tenant.events", "vishop.user.events"},
      groupId = CONSUMER_GROUP,
      autoStartup = "${spring.kafka.listener.auto-startup:true}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onEvent(ConsumerRecord<String, String> record) {
    try {
      EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);

      idempotentConsumer.executeIfNotProcessed(
          envelope.eventId(),
          CONSUMER_GROUP,
          envelope.eventType(),
          () -> {
            AuditLogEntity auditLog =
                new AuditLogEntity(
                    UuidV7.nextId(),
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.tenantId(),
                    envelope.aggregateType(),
                    envelope.aggregateId(),
                    envelope.producer(),
                    envelope.payload() != null ? envelope.payload().toString() : "{}",
                    envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now(),
                    Instant.now());
            auditLogRepository.save(auditLog);
            log.info(
                "Recorded audit log for event [{}] type [{}]",
                envelope.eventId(),
                envelope.eventType());
          });
    } catch (Exception e) {
      log.error(
          "Failed to process audit event from topic [{}] offset [{}]",
          record.topic(),
          record.offset(),
          e);
      throw new RuntimeException("Error processing audit event", e);
    }
  }
  // #endregion
}
