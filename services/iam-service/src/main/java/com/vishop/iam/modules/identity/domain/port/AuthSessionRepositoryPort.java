package com.vishop.iam.modules.identity.domain.port;

import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepositoryPort {
  AuthSession create(UUID userId, long tokenVersion, Instant expiresAt);

  Optional<AuthSession> find(UUID id);

  /** Chỉ lần đầu đổi ACTIVE -> REVOKED trả true; request logout lặp không tạo thêm audit. */
  boolean revoke(UUID sessionId, UUID userId);
}
