package com.vishop.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Verifies RS256 using cached IAM public keys. No signing secret or private key is held here. */
@Component
public final class JwksTokenVerifier implements GatewayTokenVerifier {
  private final NimbusReactiveJwtDecoder decoder;

  public JwksTokenVerifier(
      WebClient.Builder client,
      @Value("${vishop.security.jwt.jwk-set-uri}") String uri,
      @Value("${vishop.security.jwt.jwks-cache-ttl:5m}") Duration ttl,
      @Value("${vishop.security.jwt.jwks-refresh-interval:10s}") Duration refreshInterval) {
    var cache = new JwksPublicKeyCache(client.build(), uri, ttl, refreshInterval);
    decoder = NimbusReactiveJwtDecoder.withJwkSource(jwt -> {
      var header = jwt.getHeader();
      String kid = header.getKeyID();
      if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || kid == null || kid.isBlank())
        return Flux.error(new AuthenticationFailure(401, "unauthorized"));
      return cache.get(kid).flatMapMany(set -> Flux.fromIterable(set.getKeys()))
          .filter(key -> kid.equals(key.getKeyID()) && key instanceof RSAKey rsa
              && !rsa.isPrivate() && rsa.size() >= 2048
              && (key.getKeyUse() == null || KeyUse.SIGNATURE.equals(key.getKeyUse()))
              && (key.getAlgorithm() == null || JWSAlgorithm.RS256.equals(key.getAlgorithm())));
    }).build();
    decoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
  }

  @Override
  public Mono<Claims> verify(String credential) {
    return Mono.defer(() -> decoder.decode(credential))
        .map(jwt -> {
          var claims = new HashMap<>(jwt.getClaims());
          if (jwt.getIssuedAt() != null) claims.put("iat", Date.from(jwt.getIssuedAt()));
          if (jwt.getExpiresAt() != null) claims.put("exp", Date.from(jwt.getExpiresAt()));
          if (jwt.getNotBefore() != null) claims.put("nbf", Date.from(jwt.getNotBefore()));
          return Jwts.claims().add(claims).build();
        })
        .onErrorMap(error -> {
          for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwksPublicKeyCache.Unavailable) return cause;
          }
          if (error instanceof JwtException || error instanceof IllegalArgumentException)
            return new AuthenticationFailure(401, "unauthorized");
          return error;
        });
  }
}
