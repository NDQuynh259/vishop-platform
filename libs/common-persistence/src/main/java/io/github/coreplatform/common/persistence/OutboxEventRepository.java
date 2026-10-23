package io.github.coreplatform.common.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  // #region findPendingEvents
  @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'PENDING' ORDER BY e.occurredAt ASC")
  List<OutboxEventEntity> findPendingEvents(Pageable pageable);

  // #endregion

  // #region findPendingEventsByTenant
  @Query(
      "SELECT e FROM OutboxEventEntity e WHERE e.status = 'PENDING' AND e.tenantId = :tenantId ORDER BY e.occurredAt ASC")
  List<OutboxEventEntity> findPendingEventsByTenant(
      @Param("tenantId") UUID tenantId, Pageable pageable);
  // #endregion
}
