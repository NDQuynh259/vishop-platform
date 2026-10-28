package com.vishop.audit.modules.auditlog.application.command;

import com.vishop.audit.modules.auditlog.application.port.AuditMessageProcessingPort;
import com.vishop.audit.modules.auditlog.domain.entity.AuditLog;
import com.vishop.audit.modules.auditlog.domain.port.AuditLogRepositoryPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordAuditLogCommandHandler implements CommandHandler<RecordAuditLogCommand, Void> {
  private final AuditLogRepositoryPort logs;
  private final AuditMessageProcessingPort messages;

  public RecordAuditLogCommandHandler(
      AuditLogRepositoryPort logs, AuditMessageProcessingPort messages) {
    this.logs = logs;
    this.messages = messages;
  }

  @Override
  @Transactional
  public Void handle(RecordAuditLogCommand command) {
    messages.executeOnce(
        command.eventId(),
        command.eventType(),
        () -> {
          Instant now = Instant.now();
          logs.append(
              new AuditLog(
                  UuidV7.nextId(),
                  command.eventId(),
                  command.eventType(),
                  command.tenantId(),
                  command.aggregateType(),
                  command.aggregateId(),
                  command.producer(),
                  command.payload(),
                  command.occurredAt() != null ? command.occurredAt() : now,
                  now));
        });
    return null;
  }
}
