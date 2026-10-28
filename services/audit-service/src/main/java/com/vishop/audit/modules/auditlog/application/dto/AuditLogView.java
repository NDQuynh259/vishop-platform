package com.vishop.audit.modules.auditlog.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogView(
    UUID id,
    UUID eventId,
    String eventType,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String producer,
    String payload,
    Instant occurredAt,
    Instant createdAt) {}
