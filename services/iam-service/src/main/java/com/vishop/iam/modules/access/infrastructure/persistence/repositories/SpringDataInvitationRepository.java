package com.vishop.iam.modules.access.infrastructure.persistence.repositories;

import com.vishop.iam.modules.access.infrastructure.persistence.entities.InvitationJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataInvitationRepository extends JpaRepository<InvitationJpaEntity, UUID> {
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE tenant_invitations SET status='EXPIRED',updated_at=now(),version=version+1 WHERE tenant_id=:tenant AND lower(email)=lower(:email) AND status='PENDING' AND expires_at<=clock_timestamp()",
      nativeQuery = true)
  void expire(@Param("tenant") UUID tenant, @Param("email") String email);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO tenant_invitations(id,tenant_id,email,role_id,invited_by,token_hash,expires_at) VALUES (:id,:tenant,:email,:role,:inviter,:hash,now()+interval '7 days')",
      nativeQuery = true)
  void insert(
      @Param("id") UUID id,
      @Param("tenant") UUID tenant,
      @Param("email") String email,
      @Param("role") UUID role,
      @Param("inviter") UUID inviter,
      @Param("hash") String hash);

  @Query(
      value =
          "SELECT * FROM tenant_invitations WHERE token_hash=:hash AND status='PENDING' AND expires_at>clock_timestamp()",
      nativeQuery = true)
  Optional<InvitationJpaEntity> active(@Param("hash") String hash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE tenant_invitations SET status='ACCEPTED',accepted_by=:user,accepted_at=clock_timestamp(),updated_at=now(),version=version+1 WHERE id=:id AND status='PENDING' AND expires_at>clock_timestamp()",
      nativeQuery = true)
  int accept(@Param("id") UUID id, @Param("user") UUID user);
}
