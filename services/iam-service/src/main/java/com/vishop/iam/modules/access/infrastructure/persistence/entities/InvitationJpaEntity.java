package com.vishop.iam.modules.access.infrastructure.persistence.entities;

import com.vishop.iam.modules.access.domain.entity.Invitation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_invitations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version")
  private Long version;

  @PrePersist
  private void beforeInsert() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }

  public Invitation toDomain() {
    return new Invitation(getId(), tenantId, email, roleId, invitedBy, status, expiresAt);
  }
}
