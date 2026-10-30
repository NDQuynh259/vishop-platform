package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Access/refresh JWTs with independent HS256 keys. Services own business claims and lifetimes. */
public final class JwtTokenService {
  private final JwtTokenProvider accessTokens;
  private final JwtTokenProvider refreshTokens;
  private final Clock clock;

  public JwtTokenService(String accessSecret, String refreshSecret) {
    this(accessSecret, refreshSecret, Clock.systemUTC());
  }

  JwtTokenService(String accessSecret, String refreshSecret, Clock clock) {
    if (Objects.equals(accessSecret, refreshSecret))
      throw new IllegalArgumentException("Access and refresh secrets must be different");
    accessTokens = new JwtTokenProvider(accessSecret);
    refreshTokens = new JwtTokenProvider(refreshSecret);
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public String generateAccessToken(Map<String, ?> claims, Duration lifetime) {
    return generate(accessTokens, claims, lifetime);
  }

  public String generateRefreshToken(Map<String, ?> claims, Duration lifetime) {
    return generate(refreshTokens, claims, lifetime);
  }

  public Claims verifyAccessToken(String token) {
    return verify(accessTokens, token);
  }

  public Claims verifyRefreshToken(String token) {
    return verify(refreshTokens, token);
  }

  /** Sets iat/exp at JWT second precision; preserves a caller-provided jti or generates one. */
  private String generate(JwtTokenProvider provider, Map<String, ?> claims, Duration lifetime) {
    Objects.requireNonNull(claims, "claims");
    if (lifetime == null || lifetime.compareTo(Duration.ofSeconds(1)) < 0)
      throw new IllegalArgumentException("Token lifetime must be at least one second");
    Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    return generateUntil(provider, claims, issuedAt, issuedAt.plusSeconds(lifetime.getSeconds()));
  }

  public String generateAccessToken(Map<String, ?> claims, Instant expiresAt) {
    Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    return generateUntil(accessTokens, claims, issuedAt, expiresAt);
  }

  /** Giữ đúng deadline có sẵn, dùng khi đổi tenant mà không kéo dài tuổi thọ phiên. */
  public String generateRefreshToken(Map<String, ?> claims, Instant expiresAt) {
    Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    return generateUntil(refreshTokens, claims, issuedAt, expiresAt);
  }

  private String generateUntil(
      JwtTokenProvider provider, Map<String, ?> claims, Instant issuedAt, Instant expiresAt) {
    Objects.requireNonNull(claims, "claims");
    if (expiresAt == null || !expiresAt.truncatedTo(ChronoUnit.SECONDS).isAfter(issuedAt))
      throw new IllegalArgumentException("Token expiration must be after issuance");
    var payload = new HashMap<String, Object>(claims);
    payload.put("iat", Date.from(issuedAt));
    payload.put("exp", Date.from(expiresAt.truncatedTo(ChronoUnit.SECONDS)));
    payload.putIfAbsent("jti", UUID.randomUUID().toString());
    return provider.encode(payload);
  }

  private Claims verify(JwtTokenProvider provider, String token) {
    Claims claims = provider.verify(token);
    if (claims.getIssuedAt() == null
        || claims.getExpiration() == null
        || !claims.getExpiration().after(claims.getIssuedAt()))
      throw new IllegalArgumentException("Token requires valid issued-at and expiration times");
    return claims;
  }
}
