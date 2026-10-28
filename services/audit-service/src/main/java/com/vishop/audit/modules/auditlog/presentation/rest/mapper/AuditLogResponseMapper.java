package com.vishop.audit.modules.auditlog.presentation.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.presentation.rest.dto.AuditLogResponse;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import org.springframework.stereotype.Component;

@Component
public class AuditLogResponseMapper {
  private final ObjectMapper json;

  public AuditLogResponseMapper(ObjectMapper json) {
    this.json = json;
  }

  public AuditLogResponse from(AuditLogView log) {
    try {
      return new AuditLogResponse(
          log.id(),
          log.eventId(),
          log.eventType(),
          log.tenantId(),
          log.aggregateType(),
          log.aggregateId(),
          log.producer(),
          json.readTree(log.payload()),
          log.occurredAt(),
          log.createdAt());
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Stored audit payload is not JSON", failure);
    }
  }

  public PagedResult<AuditLogResponse> fromPage(PagedResult<AuditLogView> page) {
    return new PagedResult<>(
        page.items().stream().map(this::from).toList(),
        page.hasNextPage(),
        page.nextCursor(),
        page.page(),
        page.limit(),
        page.total(),
        page.totalPages());
  }
}
