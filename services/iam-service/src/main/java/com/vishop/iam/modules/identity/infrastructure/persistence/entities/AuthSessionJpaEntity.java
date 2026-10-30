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
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_version", nullable = false)
  private long tokenVersion;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Version private Long version;

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
    return new AuthSession(id, userId, tokenVersion, expiresAt, revokedAt);
  }
}
