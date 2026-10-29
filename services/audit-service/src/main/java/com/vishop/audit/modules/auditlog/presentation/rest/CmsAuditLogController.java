package com.vishop.audit.modules.auditlog.presentation.rest;

import com.vishop.audit.modules.auditlog.application.dto.CmsAuditActor;
import com.vishop.audit.modules.auditlog.application.query.ListCmsAuditLogsQuery;
import com.vishop.audit.modules.auditlog.presentation.rest.dto.AuditLogResponse;
import com.vishop.audit.modules.auditlog.presentation.rest.mapper.AuditLogResponseMapper;
import io.github.coreplatform.common.domain.constants.ApiPaths;
import io.github.coreplatform.common.domain.cqrs.QueryBus;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.web.pagination.ListEnvelope;
import io.github.coreplatform.common.web.pagination.PaginationRequest;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/cms/audit-logs")
@RequiredArgsConstructor
public class CmsAuditLogController {
  private final QueryBus queries;
  private final AuditLogResponseMapper mapper;

  @GetMapping
  @Operation(
      summary = "List audit logs for CMS",
      description =
          "PLATFORM_ADMIN only. Optional tenant/event/aggregate filters. occurredAt range is [from, to). Offset pagination uses system defaults and normalization.")
  public ListEnvelope<AuditLogResponse> list(
      @AuthenticationPrincipal GatewayPrincipal actor,
      @RequestParam(required = false) UUID tenantId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) UUID aggregateId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @ParameterObject PaginationRequest pagination) {
    if (pagination.hasCursor()) {
      throw new BadRequestException(
          "CMS audit list supports offset pagination; use page and limit");
    }
    var result =
        queries.dispatch(
            new ListCmsAuditLogsQuery(
                new CmsAuditActor(actor.userId(), actor.systemRole()),
                tenantId,
                eventType,
                aggregateType,
                aggregateId,
                from,
                to,
                pagination.toOffsetPagination()));
    return ListEnvelope.from(ApiPaths.V1 + "/cms/audit-logs", mapper.fromPage(result));
  }
}
