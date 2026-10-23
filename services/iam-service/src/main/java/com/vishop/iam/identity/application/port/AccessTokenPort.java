package com.vishop.iam.identity.application.port;

import com.vishop.iam.shared.application.dto.IamActor;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokenPort {
  record Identity(UUID userId, UUID sessionId, UUID tenantId) {}

  Identity verify(String credential);

  String issue(IamActor actor, Instant sessionExpiresAt);

  long expirationMs();
}
