package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.UUID;

/** Verifies IAM token signatures and claim contracts; performs no persistence reads. */
final class IamTokenClaimsParser {
  static final String ISSUER = "iam-service";

  private IamTokenClaimsParser() {}

  static IamTokenClaims.AccessToken verifyAccessToken(JwtTokenProvider jwt, String credential) {
    Claims claims = jwt.verify(credential);
    validateMetadata(claims, Purpose.ACCESS);
    if (claims.containsKey("roles"))
      throw new IllegalArgumentException("Legacy roles claim is not supported");
    String role = claims.get("tenantRoleId", String.class);
    return new IamTokenClaims.AccessToken(
        UUID.fromString(claims.getSubject()),
        readTenantId(claims),
        SystemRole.valueOf(claims.get("systemRole", String.class)),
        role == null ? null : UUID.fromString(role),
        claims.get("tenantRole", String.class),
        readTokenVersion(claims));
  }

  static IamTokenClaims.RefreshToken verifyRefreshToken(JwtTokenProvider jwt, String credential) {
    Claims claims = jwt.verify(credential);
    validateMetadata(claims, Purpose.REFRESH);
    return new IamTokenClaims.RefreshToken(
        UUID.fromString(claims.getSubject()),
        readTenantId(claims),
        readTokenVersion(claims),
        claims.getExpiration().toInstant());
  }

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

  private static void validateMetadata(Claims claims, Purpose purpose) {
    if (!ISSUER.equals(claims.getIssuer())) {
      throw new IllegalArgumentException("Invalid IAM issuer");
    }
    if (claims.getAudience() == null || !claims.getAudience().contains(purpose.audience)) {
      throw new IllegalArgumentException("Invalid IAM audience");
    }
    if (!purpose.use.equals(claims.get("token_use"))) {
      throw new IllegalArgumentException("Invalid IAM token purpose");
    }
    if (claims.getIssuedAt() == null) {
      throw new IllegalArgumentException("Missing issued-at time");
    }
    if (claims.getExpiration() == null) {
      throw new IllegalArgumentException("Missing expiration time");
    }
    if (purpose == Purpose.REFRESH && claims.getId() == null) {
      throw new IllegalArgumentException("Refresh token requires a JWT ID");
    }
  }

  private static UUID readTenantId(Claims claims) {
    String tenantId = claims.get("tenantId", String.class);
    return tenantId == null ? null : UUID.fromString(tenantId);
  }

  private static long readTokenVersion(Claims claims) {
    if (!(claims.get("tokenVersion") instanceof Number version)) {
      throw new IllegalArgumentException("Credential version must be a number");
    }
    long value = version.longValue();
    if (value < 1) {
      throw new IllegalArgumentException("Invalid credential version");
    }
    return value;
  }
}
