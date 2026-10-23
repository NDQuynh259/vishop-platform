package com.vishop.audit.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

  // #region findByTenantIdOrderByOccurredAtDesc
  List<AuditLogEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

  // #endregion

  // #region findByAggregateTypeAndAggregateIdOrderByOccurredAtDesc
  List<AuditLogEntity> findByAggregateTypeAndAggregateIdOrderByOccurredAtDesc(
      String aggregateType, UUID aggregateId, Pageable pageable);
  // #endregion
}
