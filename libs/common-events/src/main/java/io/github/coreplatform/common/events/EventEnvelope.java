package io.github.coreplatform.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.domain.UuidV7;
import java.time.Instant;
import java.util.UUID;

/**
 * Versioned envelope shared by all Kafka domain and integration events. Provides consistent
 * metadata across all microservices: eventId, eventType, tenantId, aggregateType, correlationId,
 * causationId, and JSON payload.
 */
public record EventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String producer,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    UUID correlationId,
    UUID causationId,
    JsonNode payload) {

  // #region create
  public static EventEnvelope create(
      String eventType,
      String producer,
      UUID tenantId,
      String aggregateType,
      UUID aggregateId,
      UUID correlationId,
      UUID causationId,
      JsonNode payload) {
    return new EventEnvelope(
        UuidV7.nextId(),
        eventType,
        1,
        Instant.now(),
        producer,
        tenantId,
        aggregateType,
        aggregateId,
        correlationId,
        causationId,
        payload);
  }

  // #endregion

  // #region fromDomainEvent
  /** Converts a domain event to an EventEnvelope. */
  public static EventEnvelope fromDomainEvent(
      DomainEvent domainEvent,
      String producer,
      UUID correlationId,
      UUID causationId,
      ObjectMapper objectMapper) {
    JsonNode payloadNode = objectMapper.valueToTree(domainEvent);
    return new EventEnvelope(
        domainEvent.eventId() != null ? domainEvent.eventId() : UuidV7.nextId(),
        domainEvent.eventType(),
        1,
        domainEvent.occurredAt() != null ? domainEvent.occurredAt() : Instant.now(),
        producer,
        domainEvent.tenantId(),
        domainEvent.aggregateType(),
        domainEvent.aggregateId(),
        correlationId,
        causationId,
        payloadNode);
  }

  // #endregion

  // #region getPayloadAs
  /** Extracts and deserializes the payload into a typed object. */
  public <T> T getPayloadAs(Class<T> targetClass, ObjectMapper objectMapper) {
    try {
      return objectMapper.treeToValue(this.payload, targetClass);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "Failed to deserialize event payload to " + targetClass.getName(), e);
    }
  }
  // #endregion
}
