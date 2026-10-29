package com.vishop.iam.modules.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.UUID;

/** Common IAM credential metadata, read after signature and lifetime verification. */
record IamTokenClaims(UUID userId, UUID tenantId, long tokenVersion, Instant expiresAt) {
  static final String ISSUER = "iam-service";

  enum Purpose {
    ACCESS("access", "vishop-api"),
    REFRESH("refresh", "vishop-refresh");

    final String use;
    final String audience;

    Purpose(String use, String audience) {
      this.use = use;
      this.audience = audience;
    }
  }

  static IamTokenClaims fromClaims(Claims claims, Purpose purpose) {
    if (!ISSUER.equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains(purpose.audience)
        || !purpose.use.equals(claims.get("token_use"))
        || claims.getIssuedAt() == null
        || claims.getExpiration() == null
        || (purpose == Purpose.REFRESH && claims.getId() == null))
      throw new IllegalArgumentException("Invalid IAM credential metadata");
    if (!(claims.get("tokenVersion") instanceof Number version) || version.longValue() < 1)
      throw new IllegalArgumentException("Invalid credential version");
    String tenant = claims.get("tenantId", String.class);
    return new IamTokenClaims(
        UUID.fromString(claims.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        version.longValue(),
        claims.getExpiration().toInstant());
  }
}
