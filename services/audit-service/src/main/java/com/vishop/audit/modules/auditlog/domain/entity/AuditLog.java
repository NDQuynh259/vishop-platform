package com.vishop.audit.modules.auditlog.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable record of a business event. Audit records cannot be edited. */
public record AuditLog(
    UUID id,
    UUID eventId,
    String eventType,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String producer,
    String payload,
    Instant occurredAt,
    Instant createdAt) {
  public AuditLog {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
