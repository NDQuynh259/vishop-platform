package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {
  private final JwtTokenProvider tokens;
  private final long refreshExpirationMs;

  public JwtAccessTokenAdapter(
      JwtTokenProvider tokens,
      @Value("${vishop.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    if (refreshExpirationMs <= tokens.getExpirationMs())
      throw new IllegalArgumentException("Refresh lifetime must exceed access lifetime");
    this.tokens = tokens;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public AuthenticatedUser verify(String credential) {
    try {
      var c = VerifiedAccessToken.verify(tokens, credential);
      return new AuthenticatedUser(
          c.userId(), c.tenantId(), c.systemRole(), c.roles(), c.permissions());
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  public RefreshIdentity verifyRefresh(String credential) {
    try {
      var c = tokens.parseClaims(credential);
      if (!"iam-service".equals(c.getIssuer())
          || c.getAudience() == null
          || !c.getAudience().contains("vishop-refresh")
          || !"refresh".equals(c.get("token_use"))
          || c.getExpiration() == null
          || c.getIssuedAt() == null
          || c.getId() == null) throw new IllegalArgumentException();
      String tenant = c.get("tenantId", String.class);
      return new RefreshIdentity(
          UUID.fromString(c.getSubject()),
          tenant == null ? null : UUID.fromString(tenant),
          c.getExpiration().toInstant());
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid refresh token");
    }
  }

  public String issue(AuthenticatedUser actor) {
    return tokens.generateIamAccessToken(
        actor.userId(), actor.tenantId(), actor.systemRole(), actor.roles(), actor.permissions());
  }

  public String issueRefresh(UUID userId, UUID tenantId, Instant expiresAt) {
    return tokens.generateRefreshToken(userId, tenantId, expiresAt);
  }

  public long expirationMs() {
    return tokens.getExpirationMs();
  }

  public long refreshExpirationMs() {
    return refreshExpirationMs;
  }
}
