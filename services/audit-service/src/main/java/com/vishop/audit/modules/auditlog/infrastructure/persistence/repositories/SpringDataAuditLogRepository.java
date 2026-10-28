package com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories;

import com.vishop.audit.modules.auditlog.infrastructure.persistence.entities.AuditLogJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataAuditLogRepository
    extends JpaRepository<AuditLogJpaEntity, UUID>, JpaSpecificationExecutor<AuditLogJpaEntity> {}
