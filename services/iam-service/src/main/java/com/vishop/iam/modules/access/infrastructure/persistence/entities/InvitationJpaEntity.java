package com.vishop.iam.modules.access.infrastructure.persistence.entities;

import com.vishop.iam.modules.access.domain.entity.Invitation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_invitations")
public class InvitationJpaEntity {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String email;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_by")
  private UUID acceptedBy;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected InvitationJpaEntity() {}

  public Invitation toDomain() {
    return new Invitation(id, tenantId, email, roleId, invitedBy, status, expiresAt);
  }
}
