package com.vishop.gateway.security;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.github.coreplatform.common.security.jwt.VerifiedAccessToken;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Verifies access JWTs locally without database, session, or Redis lookups. */
@Component
public class LocalAuthenticationVerifier {
  public record Identity(UUID userId, UUID tenantId, String accessToken) {}

  private final JwtTokenProvider tokens;

  public LocalAuthenticationVerifier(JwtTokenProvider tokens) {
    this.tokens = tokens;
  }

  public Mono<Identity> authenticate(String credential, boolean bearer) {
    return Mono.fromCallable(
        () -> {
          if (!bearer || credential == null || credential.isBlank())
            throw new AuthenticationFailure(401, "unauthorized");
          try {
            var claims = VerifiedAccessToken.verify(tokens, credential);
            return new Identity(claims.userId(), claims.tenantId(), credential);
          } catch (RuntimeException invalid) {
            throw new AuthenticationFailure(401, "unauthorized");
          }
        });
  }
}
