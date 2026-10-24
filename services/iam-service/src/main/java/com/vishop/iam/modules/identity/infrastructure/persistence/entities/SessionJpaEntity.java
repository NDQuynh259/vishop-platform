package com.vishop.iam.modules.identity.infrastructure.persistence.entities;

import com.vishop.iam.modules.identity.domain.entity.Session;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionJpaEntity {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "active_tenant_id")
  private UUID activeTenantId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SessionJpaEntity() {}

  public Session toDomain() {
    return new Session(id, userId, activeTenantId, expiresAt);
  }
}
