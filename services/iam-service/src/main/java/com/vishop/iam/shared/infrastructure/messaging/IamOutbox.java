package com.vishop.iam.shared.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import com.vishop.iam.shared.domain.event.IamDomainEvent;
import io.github.coreplatform.common.domain.DomainEvent;
import io.github.coreplatform.common.events.EventEnvelope;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IamOutbox implements IamEventPublisherPort {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public IamOutbox(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(String type, String aggregate, UUID id, UUID tenant, Map<String, ?> payload) {
    var envelope =
        EventEnvelope.create(
            type, "iam-service", tenant, aggregate, id, null, null, json.valueToTree(payload));
    persist(envelope);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(DomainEvent event) {
    persist(
        new EventEnvelope(
            event.eventId(),
            event.eventType(),
            1,
            event.occurredAt(),
            "iam-service",
            event.tenantId(),
            event.aggregateType(),
            event.aggregateId(),
            null,
            null,
            json.valueToTree(event instanceof IamDomainEvent data ? data.payload() : event)));
  }

  private void persist(EventEnvelope envelope) {
    try {
      db.update(
          "INSERT INTO outbox_events(id,tenant_id,event_type,aggregate_type,aggregate_id,payload,occurred_at) "
              + "VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",
          envelope.eventId(),
          envelope.tenantId(),
          envelope.eventType(),
          envelope.aggregateType(),
          envelope.aggregateId(),
          json.writeValueAsString(envelope),
          java.sql.Timestamp.from(envelope.occurredAt()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize IAM event", e);
    }
  }
}
