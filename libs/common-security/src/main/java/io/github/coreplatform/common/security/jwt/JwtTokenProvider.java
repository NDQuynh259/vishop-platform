package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;

/** JWT cryptography only. Callers own claim names, token purposes and issuance policies. */
public final class JwtTokenProvider {
  private final SecretKey key;

  public JwtTokenProvider(String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    key = Keys.hmacShaKeyFor(bytes);
  }

  public String encode(Map<String, ?> claims) {
    return Jwts.builder().claims(claims).signWith(key).compact();
  }

  /** Verifies signature and standard exp/nbf constraints when present; returns verified claims. */
  public Claims verify(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** Decodes only after verification; never exposes unsigned claims as trusted data. */
  public Claims decode(String token) {
    return verify(token);
  }
}
