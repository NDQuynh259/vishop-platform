package com.vishop.iam.modules.identity.infrastructure.persistence.repositories;

import com.vishop.iam.modules.identity.infrastructure.persistence.entities.UserJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {
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
