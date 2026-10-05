package com.vishop.tenant.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT t FROM TenantJpaEntity t WHERE " +
           "(:cursorCreatedAt IS NULL OR t.createdAt < :cursorCreatedAt OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorId)) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<TenantJpaEntity> findTenantsCursor(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
