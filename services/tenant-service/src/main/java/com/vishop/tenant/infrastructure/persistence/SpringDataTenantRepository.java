package com.vishop.tenant.infrastructure.persistence;

import com.vishop.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

    // #region findByCode
    Optional<TenantJpaEntity> findByCode(String code);
    // #endregion

    // #region existsByCode
    boolean existsByCode(String code);
    // #endregion

    // #region findTenantsCursor
    @Query("SELECT t FROM TenantJpaEntity t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:cursorCreatedAt IS NULL OR t.createdAt < :cursorCreatedAt OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorId)) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<TenantJpaEntity> findTenantsCursor(
            @Param("status") TenantStatus status,
            @Param("search") String search,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
    // #endregion

    // #region findTenantsOffset
    @Query(value = "SELECT t FROM TenantJpaEntity t WHERE " +
                   "(:status IS NULL OR t.status = :status) AND " +
                   "(:search IS NULL OR :search = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "ORDER BY t.createdAt DESC, t.id DESC",
           countQuery = "SELECT COUNT(t) FROM TenantJpaEntity t WHERE " +
                        "(:status IS NULL OR t.status = :status) AND " +
                        "(:search IS NULL OR :search = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<TenantJpaEntity> findTenantsOffset(
            @Param("status") TenantStatus status,
            @Param("search") String search,
            Pageable pageable);
    // #endregion
}
