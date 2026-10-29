package com.vishop.gateway.security;

import io.jsonwebtoken.Claims;
import java.util.UUID;

/**
 * Gateway's accepted IAM credential contract. Authorization grants come from
 * Redis.
 */
record IamAccessTokenClaims(UUID userId, UUID tenantId, long tokenVersion) {
  static IamAccessTokenClaims from(Claims claims) {
    if (!"iam-service".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("vishop-api")
        || !"access".equals(claims.get("token_use"))
        || claims.getIssuedAt() == null
        || claims.getExpiration() == null)
      throw new IllegalArgumentException("Invalid IAM access credential");
    if (!(claims.get("tokenVersion") instanceof Number version) || version.longValue() < 1)
      throw new IllegalArgumentException("Invalid credential version");
    String tenant = claims.get("tenantId", String.class);
    return new IamAccessTokenClaims(
        UUID.fromString(claims.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        version.longValue());
  }
}
