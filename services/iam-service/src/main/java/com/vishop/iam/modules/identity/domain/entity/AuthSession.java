package com.vishop.iam.modules.identity.domain.entity;

import java.time.Instant;
import java.util.UUID;

/** Một lần đăng nhập trên một thiết bị. JWT chỉ mang id; trạng thái thu hồi nằm ở server. */
public record AuthSession(
    UUID id, UUID userId, long tokenVersion, Instant expiresAt, Instant revokedAt) {
  public boolean active(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }
}
