package io.github.coreplatform.common.saga.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaInstanceRepository extends JpaRepository<SagaInstanceEntity, UUID> {

    // #region findByCorrelationId
    Optional<SagaInstanceEntity> findByCorrelationId(UUID correlationId);
    // #endregion
}
