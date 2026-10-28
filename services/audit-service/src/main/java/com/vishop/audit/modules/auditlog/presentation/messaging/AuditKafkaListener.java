package com.vishop.audit.modules.auditlog.presentation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.audit.modules.auditlog.application.command.RecordAuditLogCommand;
import io.github.coreplatform.common.domain.cqrs.CommandBus;
import io.github.coreplatform.common.events.EventEnvelope;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Inbound adapter: translates the integration event into an application
 * command.
 */
@Component
@Slf4j
public class AuditKafkaListener {
  private final ObjectMapper json;
  private final CommandBus commands;

  public AuditKafkaListener(ObjectMapper json, CommandBus commands) {
    this.json = json;
    this.commands = commands;
  }

  @KafkaListener(topics = { "vishop.tenant.events",
      "vishop.user.events" }, groupId = "audit-service", autoStartup = "${spring.kafka.listener.auto-startup:true}", containerFactory = "kafkaListenerContainerFactory")
  public void onEvent(ConsumerRecord<String, String> record) {
    try {
      EventEnvelope event = json.readValue(record.value(), EventEnvelope.class);
      log.info(
          "Received event {} of type {} for aggregate {} of type {}",
          event.eventId(),
          event.eventType(),
          event.aggregateId(),
          event.aggregateType());

      commands.dispatch(
          new RecordAuditLogCommand(
              event.eventId(),
              event.eventType(),
              event.tenantId(),
              event.aggregateType(),
              event.aggregateId(),
              event.producer(),
              event.payload() != null ? event.payload().toString() : "{}",
              event.occurredAt()));
    } catch (JsonProcessingException failure) {
      throw new IllegalArgumentException("Cannot deserialize audit event", failure);
    }
  }
}
