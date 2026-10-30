package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;

/** HS256 cryptography only. Callers own claims, token purpose and key selection. */
public final class JwtTokenProvider {
  private final SecretKey key;
  private final JwtParser parser;

  public JwtTokenProvider(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    parser =
        Jwts.parser()
            .keyLocator(
                header -> {
                  if (!"HS256".equals(header.get("alg")))
                    throw new UnsupportedJwtException("Only HS256 tokens are supported");
                  return key;
                })
            .build();
  }

  public String encode(Map<String, ?> claims) {
    return Jwts.builder().claims(claims).signWith(key, Jwts.SIG.HS256).compact();
  }

  /** Verifies HS256 signature and standard exp/nbf constraints; returns verified claims. */
  public Claims verify(String token) {
    return parser.parseSignedClaims(token).getPayload();
  }

  /** Decodes only after verification; never exposes unsigned claims as trusted data. */
  public Claims decode(String token) {
    return verify(token);
  }
}
