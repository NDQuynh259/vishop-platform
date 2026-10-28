package com.vishop.audit.modules.auditlog.infrastructure.persistence.adapters;

import com.vishop.audit.modules.auditlog.domain.entity.AuditLog;
import com.vishop.audit.modules.auditlog.domain.port.AuditLogRepositoryPort;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.entities.AuditLogJpaEntity;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories.SpringDataAuditLogRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepositoryPort {
  private final SpringDataAuditLogRepository repository;

  public AuditLogRepositoryAdapter(SpringDataAuditLogRepository repository) {
    this.repository = repository;
  }

  @Override
  public void append(AuditLog log) {
    repository.save(
        new AuditLogJpaEntity(
            log.id(),
            log.eventId(),
            log.eventType(),
            log.tenantId(),
            log.aggregateType(),
            log.aggregateId(),
            log.producer(),
            log.payload(),
            log.occurredAt(),
            log.createdAt()));
  }
}
