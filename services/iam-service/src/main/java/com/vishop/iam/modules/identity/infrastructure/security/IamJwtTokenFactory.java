package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** IAM owns its token payload, issuer/audiences and access lifetime. */
@Component
public class IamJwtTokenFactory {
  private final JwtTokenProvider jwt;
  private final long accessExpirationMs;

  public IamJwtTokenFactory(
      JwtTokenProvider jwt,
      @Value("${vishop.security.jwt.access-expiration-ms:900000}") long accessExpirationMs) {
    if (accessExpirationMs <= 0)
      throw new IllegalArgumentException("Access lifetime must be positive");
    this.jwt = jwt;
    this.accessExpirationMs = accessExpirationMs;
  }

  public String access(AuthenticatedUser actor, UUID tenantRoleId, long tokenVersion) {
    var identity =
        new IamTokenClaims.AccessToken(
            actor.userId(),
            actor.tenantId(),
            SystemRole.valueOf(actor.systemRole()),
            tenantRoleId,
            actor.tenantRole(),
            tokenVersion);
    Instant issuedAt = Instant.now();
    var claims =
        base(
            identity.userId(),
            identity.tenantId(),
            IamTokenClaimsParser.Purpose.ACCESS,
            issuedAt,
            issuedAt.plusMillis(accessExpirationMs),
            tokenVersion);
    claims.put("systemRole", identity.systemRole().name());
    claims.put("tenantRole", identity.tenantRole());
    claims.put(
        "tenantRoleId",
        identity.tenantRoleId() == null ? null : identity.tenantRoleId().toString());
    return jwt.encode(claims);
  }

  public String refresh(UUID userId, UUID tenantId, Instant expiresAt, long tokenVersion) {
    return jwt.encode(
        base(
            userId,
            tenantId,
            IamTokenClaimsParser.Purpose.REFRESH,
            Instant.now(),
            expiresAt,
            tokenVersion));
  }

  public long accessExpirationMs() {
    return accessExpirationMs;
  }

  private Map<String, Object> base(
      UUID userId,
      UUID tenantId,
      IamTokenClaimsParser.Purpose purpose,
      Instant issuedAt,
      Instant expiry,
      long version) {
    var claims = new HashMap<String, Object>();
    claims.put("sub", userId.toString());
    claims.put("iss", IamTokenClaimsParser.ISSUER);
    claims.put("aud", List.of(purpose.audience));
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("iat", Date.from(issuedAt));
    claims.put("exp", Date.from(expiry));
    claims.put("token_use", purpose.use);
    claims.put("tokenVersion", version);
    claims.put("tenantId", tenantId == null ? null : tenantId.toString());
    return claims;
  }
}
