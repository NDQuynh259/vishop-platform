package com.vishop.iam.modules.identity.infrastructure.persistence.repositories;

import com.vishop.iam.modules.identity.infrastructure.persistence.entities.AuthSessionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataAuthSessionRepository extends JpaRepository<AuthSessionJpaEntity, UUID> {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
      UPDATE auth_sessions SET revoked_at=clock_timestamp(),version=version+1
      WHERE id=:id AND user_id=:user AND revoked_at IS NULL
      """,
      nativeQuery = true)
  int revoke(@Param("id") UUID id, @Param("user") UUID user);
}
