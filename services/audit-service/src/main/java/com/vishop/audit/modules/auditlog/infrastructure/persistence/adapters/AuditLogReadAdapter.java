package com.vishop.audit.modules.auditlog.infrastructure.persistence.adapters;

import com.vishop.audit.modules.auditlog.application.dto.AuditLogView;
import com.vishop.audit.modules.auditlog.application.port.AuditLogReadPort;
import com.vishop.audit.modules.auditlog.application.query.ListAuditLogsQuery;
import com.vishop.audit.modules.auditlog.application.query.ListCmsAuditLogsQuery;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.entities.AuditLogJpaEntity;
import com.vishop.audit.modules.auditlog.infrastructure.persistence.repositories.SpringDataAuditLogRepository;
import io.github.coreplatform.common.domain.pagination.PagedResult;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogReadAdapter implements AuditLogReadPort {
  private final SpringDataAuditLogRepository repository;

  public AuditLogReadAdapter(SpringDataAuditLogRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<AuditLogView> find(ListAuditLogsQuery query) {
    Specification<AuditLogJpaEntity> filter =
        (root, criteria, cb) -> {
          var tenant = cb.equal(root.get("tenantId"), query.tenantId());
          return query.aggregateId() == null
              ? tenant
              : cb.and(
                  tenant,
                  cb.equal(root.get("aggregateType"), query.aggregateType()),
                  cb.equal(root.get("aggregateId"), query.aggregateId()));
        };
    return repository
        .findAll(
            filter,
            PageRequest.of(
                query.page(), query.size(), Sort.by(Sort.Direction.DESC, "occurredAt", "id")))
        .stream()
        .map(
            row ->
                new AuditLogView(
                    row.getId(),
                    row.getEventId(),
                    row.getEventType(),
                    row.getTenantId(),
                    row.getAggregateType(),
                    row.getAggregateId(),
                    row.getProducer(),
                    row.getPayload(),
                    row.getOccurredAt(),
                    row.getCreatedAt()))
        .toList();
  }

  @Override
  public PagedResult<AuditLogView> findCms(ListCmsAuditLogsQuery query) {
    Specification<AuditLogJpaEntity> filter =
        (root, criteria, cb) -> {
          var predicates = new ArrayList<Predicate>();
          if (query.tenantId() != null)
            predicates.add(cb.equal(root.get("tenantId"), query.tenantId()));
          if (query.eventType() != null)
            predicates.add(cb.equal(root.get("eventType"), query.eventType()));
          if (query.aggregateType() != null) {
            predicates.add(cb.equal(root.get("aggregateType"), query.aggregateType()));
            predicates.add(cb.equal(root.get("aggregateId"), query.aggregateId()));
          }
          if (query.from() != null)
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
          if (query.to() != null) predicates.add(cb.lessThan(root.get("occurredAt"), query.to()));
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    var page =
        repository.findAll(
            filter,
            PageRequest.of(
                query.pagination().pageIndex(),
                query.pagination().limit(),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    var items =
        page.stream()
            .map(
                row ->
                    new AuditLogView(
                        row.getId(),
                        row.getEventId(),
                        row.getEventType(),
                        row.getTenantId(),
                        row.getAggregateType(),
                        row.getAggregateId(),
                        row.getProducer(),
                        row.getPayload(),
                        row.getOccurredAt(),
                        row.getCreatedAt()))
            .toList();
    return PagedResult.ofOffset(
        items, query.pagination().page(), query.pagination().limit(), page.getTotalElements());
  }
}
