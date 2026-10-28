package com.vishop.audit.modules.auditlog.presentation.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID eventId,
    String eventType,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String producer,
    JsonNode payload,
    Instant occurredAt,
    Instant createdAt) {}
