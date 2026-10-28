package com.vishop.audit.modules.auditlog.application.command;

import io.github.coreplatform.common.domain.cqrs.Command;
import java.time.Instant;
import java.util.UUID;

public record RecordAuditLogCommand(
    UUID eventId,
    String eventType,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String producer,
    String payload,
    Instant occurredAt)
    implements Command<Void> {}
