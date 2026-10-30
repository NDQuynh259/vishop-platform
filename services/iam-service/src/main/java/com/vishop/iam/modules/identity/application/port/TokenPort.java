package com.vishop.iam.modules.identity.application.port;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;

public interface TokenPort {
  /** Verifies JWT and current DB identity/version/tenant membership; returns claims only. */
  IamTokenClaims.AccessToken verifyAccessToken(String credential);

  IamTokenClaims.RefreshToken verifyRefreshToken(String credential);

  String issue(AuthenticatedUser actor, UUID tenantRoleId);

  String issueRefresh(UUID userId, UUID tenantId, Instant expiresAt);

  long expirationMs();

  long refreshExpirationMs();
}
