package com.vishop.audit.modules.auditlog.application.query;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.dto.CmsAuditActor;
import io.github.coreplatform.common.domain.cqrs.Query;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.pagination.OffsetPagination;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ListCmsAuditLogsQuery(
    CmsAuditActor actor,
    UUID tenantId,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Instant from,
    Instant to,
    OffsetPagination pagination)
    implements Query<PagedResult<AuditLogView>> {
  public ListCmsAuditLogsQuery {
    Objects.requireNonNull(pagination, "pagination");
    if ((aggregateType == null) != (aggregateId == null))
      throw new BadRequestException("Provide both aggregateType and aggregateId");
    if (aggregateType != null && (aggregateType.isBlank() || aggregateType.length() > 100))
      throw new BadRequestException("Invalid aggregateType");
    if (eventType != null && (eventType.isBlank() || eventType.length() > 200))
      throw new BadRequestException("Invalid eventType");
    if (from != null && to != null && !from.isBefore(to))
      throw new BadRequestException("from must be before to");
  }
}
