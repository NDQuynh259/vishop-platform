package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.shared.application.dto.IamActor;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {
  private final JwtTokenProvider tokens;

  public JwtAccessTokenAdapter(JwtTokenProvider tokens) {
    this.tokens = tokens;
  }

  public Identity verify(String credential) {
    try {
      var claims = tokens.parseClaims(credential);
      if (!"iam-service".equals(claims.getIssuer())
          || claims.getAudience() == null
          || !claims.getAudience().contains("vishop-api")
          || !"access".equals(claims.get("token_use"))
          || claims.getExpiration() == null) throw new IllegalArgumentException();
      String tenant = claims.get("tenantId", String.class);
      return new Identity(
          UUID.fromString(claims.getSubject()),
          UUID.fromString(claims.get("sid", String.class)),
          tenant == null ? null : UUID.fromString(tenant));
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  public String issue(IamActor actor, Instant expiresAt) {
    return tokens.generateSessionAccessToken(
        actor.userId(),
        actor.tenantId(),
        actor.systemRole(),
        actor.sessionId(),
        actor.roles(),
        actor.permissions(),
        expiresAt);
  }

  public long expirationMs() {
    return tokens.getExpirationMs();
  }
}
