package com.vishop.iam.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.tenant.infrastructure.persistence.entities.TenantJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataTenantRepository
    extends JpaRepository<TenantJpaEntity, UUID>, JpaSpecificationExecutor<TenantJpaEntity> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from TenantJpaEntity t where t.id = :id")
  Optional<TenantJpaEntity> findLockedById(@Param("id") UUID id);

  @Query(
      value = "SELECT * FROM tenants ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",
      nativeQuery = true)
  List<TenantJpaEntity> findWindow(@Param("limit") int limit, @Param("offset") int offset);
}
