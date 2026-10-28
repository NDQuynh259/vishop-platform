package com.vishop.audit.modules.auditlog.domain.port;

import com.vishop.audit.modules.auditlog.domain.entity.AuditLog;

/** Append-only persistence contract; no update or delete operations. */
public interface AuditLogRepositoryPort {
  void append(AuditLog log);
}
