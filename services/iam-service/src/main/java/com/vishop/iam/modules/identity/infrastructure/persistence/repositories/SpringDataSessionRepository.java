package com.vishop.iam.modules.identity.infrastructure.persistence.repositories;

import com.vishop.iam.modules.identity.infrastructure.persistence.entities.SessionJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataSessionRepository extends JpaRepository<SessionJpaEntity, UUID> {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES (:id,:user,:hash,now()+(:seconds * interval '1 second'))",
      nativeQuery = true)
  void insert(
      @Param("id") UUID id,
      @Param("user") UUID user,
      @Param("hash") String hash,
      @Param("seconds") long seconds);

  @Query(
      value =
          "SELECT * FROM sessions WHERE id=:id AND revoked_at IS NULL AND expires_at>clock_timestamp()",
      nativeQuery = true)
  Optional<SessionJpaEntity> activeById(@Param("id") UUID id);

  @Query(
      value =
          "SELECT * FROM sessions WHERE token_hash=:hash AND revoked_at IS NULL AND expires_at>clock_timestamp()",
      nativeQuery = true)
  Optional<SessionJpaEntity> activeByHash(@Param("hash") String hash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE sessions SET active_tenant_id=:tenant,updated_at=now() WHERE id=:id AND user_id=:user AND revoked_at IS NULL",
      nativeQuery = true)
  void selectTenant(@Param("id") UUID id, @Param("user") UUID user, @Param("tenant") UUID tenant);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE sessions SET revoked_at=now(),updated_at=now() WHERE id=:id AND user_id=:user AND revoked_at IS NULL",
      nativeQuery = true)
  void revoke(@Param("id") UUID id, @Param("user") UUID user);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE sessions SET revoked_at=clock_timestamp(),updated_at=clock_timestamp() WHERE user_id=:user AND revoked_at IS NULL",
      nativeQuery = true)
  void revokeAll(@Param("user") UUID user);
}
