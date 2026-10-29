package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.port.TokenPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements TokenPort {
  private final JwtTokenProvider tokens;
  private final IamJwtTokenFactory factory;
  private final long refreshExpirationMs;
  private final jakarta.persistence.EntityManager db;

  public JwtTokenAdapter(
      JwtTokenProvider tokens,
      IamJwtTokenFactory factory,
      jakarta.persistence.EntityManager db,
      @Value("${vishop.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    if (refreshExpirationMs <= factory.accessExpirationMs())
      throw new IllegalArgumentException("Refresh lifetime must exceed access lifetime");
    this.tokens = tokens;
    this.factory = factory;
    this.db = db;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  public AuthenticatedUser verify(String credential) {
    try {
      var c = IamAccessTokenClaims.verify(tokens, credential);
      return new AuthenticatedUser(
          c.userId(), c.tenantId(), c.systemRole(), c.tenantRole(), c.permissions());
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  public RefreshIdentity verifyRefresh(String credential) {
    try {
      var c = tokens.verify(credential);
      if (!"iam-service".equals(c.getIssuer())
          || c.getAudience() == null
          || !c.getAudience().contains("vishop-refresh")
          || !"refresh".equals(c.get("token_use"))
          || c.getExpiration() == null
          || c.getIssuedAt() == null
          || c.getId() == null) throw new IllegalArgumentException();
      UUID userId = UUID.fromString(c.getSubject());
      if (!(c.get("tokenVersion") instanceof Number version)
          || version.longValue() != version(userId))
        throw new IllegalArgumentException("Revoked refresh token");
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
    return factory.access(actor, version(actor.userId()));
  }

  public String issueRefresh(UUID userId, UUID tenantId, Instant expiresAt) {
    return factory.refresh(userId, tenantId, expiresAt, version(userId));
  }

  private long version(UUID userId) {
    return ((Number)
            db.createNativeQuery("SELECT token_version FROM users WHERE id=:id")
                .setParameter("id", userId)
                .getSingleResult())
        .longValue();
  }

  public long expirationMs() {
    return factory.accessExpirationMs();
  }

  public long refreshExpirationMs() {
    return refreshExpirationMs;
  }
}
