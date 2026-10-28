package com.vishop.audit.modules.auditlog.application.query;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.port.AuditLogReadPort;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListCmsAuditLogsQueryHandler
    implements QueryHandler<ListCmsAuditLogsQuery, PagedResult<AuditLogView>> {
  private final AuditLogReadPort logs;

  public ListCmsAuditLogsQueryHandler(AuditLogReadPort logs) {
    this.logs = logs;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<AuditLogView> handle(ListCmsAuditLogsQuery query) {
    if (query.actor() == null
        || !SystemRole.PLATFORM_ADMIN.name().equals(query.actor().systemRole()))
      throw new ForbiddenException("CMS requires PLATFORM_ADMIN");
    return logs.findCms(query);
  }
}
