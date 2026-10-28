package com.vishop.audit.modules.auditlog.application.query;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.port.AuditLogReadPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListAuditLogsQueryHandler
    implements QueryHandler<ListAuditLogsQuery, List<AuditLogView>> {
  private final AuditLogReadPort logs;

  public ListAuditLogsQueryHandler(AuditLogReadPort logs) {
    this.logs = logs;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditLogView> handle(ListAuditLogsQuery query) {
    return logs.find(query);
  }
}
