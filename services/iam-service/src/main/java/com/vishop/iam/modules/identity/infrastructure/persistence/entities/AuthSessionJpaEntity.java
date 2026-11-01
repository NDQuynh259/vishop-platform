package com.vishop.iam.modules.identity.infrastructure.persistence.entities;

import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSessionJpaEntity {
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "token_version", nullable = false, updatable = false)
  private long tokenVersion;

  @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public static AuthSessionJpaEntity create(
      UUID id, UUID userId, long tokenVersion, Instant expiresAt) {
    var row = new AuthSessionJpaEntity();
    row.id = id;
    row.userId = userId;
    row.tokenVersion = tokenVersion;
    row.expiresAt = expiresAt;
    return row;
  }

  public AuthSession toDomain() {
    return new AuthSession(id, userId, tokenVersion, createdAt, expiresAt, revokedAt);
  }
}
