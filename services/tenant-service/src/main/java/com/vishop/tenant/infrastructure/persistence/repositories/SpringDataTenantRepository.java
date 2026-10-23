package com.vishop.tenant.infrastructure.persistence.repositories;

import com.vishop.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for TenantJpaEntity.
 * Extends JpaSpecificationExecutor for pure ORM dynamic criteria querying.
 */
@Repository
public interface SpringDataTenantRepository
        extends JpaRepository<TenantJpaEntity, UUID>, JpaSpecificationExecutor<TenantJpaEntity> {

    // #region findByCode
    Optional<TenantJpaEntity> findByCode(String code);
    // #endregion

    // #region existsByCode
    boolean existsByCode(String code);
    // #endregion
}
