package com.vishop.iam.modules.identity.infrastructure.persistence.repositories;

import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {
  @Query("select u from UserJpaEntity u where u.id=:id and u.deletedAt is null")
  Optional<UserJpaEntity> activeRow(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserJpaEntity u where lower(u.email)=lower(:email) and u.deletedAt is null")
  Optional<UserJpaEntity> lockByEmail(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserJpaEntity u where u.id=:id and u.deletedAt is null")
  Optional<UserJpaEntity> lockUser(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE users SET last_tenant_id=:tenant WHERE id=:id", nativeQuery = true)
  void rememberTenant(@Param("id") UUID id, @Param("tenant") UUID tenant);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE users SET status=:status,updated_at=now(),version=version+1 WHERE id=:id",
      nativeQuery = true)
  void updateStatus(@Param("id") UUID id, @Param("status") String status);

  @Query(
      value = "SELECT count(*) FROM users WHERE system_role=:role AND status='ACTIVE'",
      nativeQuery = true)
  int activeAdmins(@Param("role") String role);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE users SET system_role=:role,updated_at=now(),version=version+1 WHERE id=:id",
      nativeQuery = true)
  void updateSystemRole(@Param("id") UUID id, @Param("role") String role);
}
