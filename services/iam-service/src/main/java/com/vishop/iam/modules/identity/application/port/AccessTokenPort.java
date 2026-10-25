package com.vishop.iam.modules.identity.application.port;

import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokenPort {
  record RefreshIdentity(UUID userId, UUID tenantId, Instant expiresAt) {}

  AuthenticatedUser verify(String credential);

  RefreshIdentity verifyRefresh(String credential);

  String issue(AuthenticatedUser actor);

  String issueRefresh(UUID userId, UUID tenantId, Instant expiresAt);

  long expirationMs();

  long refreshExpirationMs();
}
