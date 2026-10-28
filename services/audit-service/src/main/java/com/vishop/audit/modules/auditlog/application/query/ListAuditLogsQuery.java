package com.vishop.audit.modules.auditlog.application.query;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import io.github.coreplatform.common.domain.cqrs.Query;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal tenant-scoped read contract. Callers must authorize tenant access before dispatch. */
public record ListAuditLogsQuery(
    UUID tenantId, String aggregateType, UUID aggregateId, int page, int size)
    implements Query<List<AuditLogView>> {
  public ListAuditLogsQuery {
    Objects.requireNonNull(tenantId, "tenantId");
    if ((aggregateType == null) != (aggregateId == null))
      throw new IllegalArgumentException("Provide both aggregateType and aggregateId");
    if (aggregateType != null && aggregateType.isBlank())
      throw new IllegalArgumentException("aggregateType must not be blank");
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("page must be nonnegative and size between 1 and 100");
  }
}
