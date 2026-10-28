package com.vishop.audit.modules.auditlog.application.port;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.query.ListAuditLogsQuery;
import com.vishop.audit.modules.auditlog.application.query.ListCmsAuditLogsQuery;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import java.util.List;

public interface AuditLogReadPort {
  List<AuditLogView> find(ListAuditLogsQuery query);

  PagedResult<AuditLogView> findCms(ListCmsAuditLogsQuery query);
}
