package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility component for generating, signing, parsing, and validating JWT access tokens. Used by
 * iam-service for token issuance and downstream services for verification.
 */
@Component
public class JwtTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

  public static final String CLAIM_TENANT_ID = "tenantId";
  public static final String CLAIM_EMAIL = "email";
  public static final String CLAIM_FULL_NAME = "fullName";
  public static final String CLAIM_ROLES = "roles";
  public static final String CLAIM_PERMISSIONS = "permissions";

  private final SecretKey key;
  private final long expirationMs;

  public JwtTokenProvider(
      @Value("${vishop.security.jwt.secret:${JWT_SECRET:}}") String secret,
      @Value("${vishop.security.jwt.expiration-ms:86400000}") long expirationMs) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirationMs = expirationMs;
  }

  // #region generateAccessToken
  public String generateAccessToken(
      UUID userId,
      UUID tenantId,
      String email,
      String fullName,
      List<String> roles,
      List<String> permissions) {

    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_TENANT_ID, tenantId.toString())
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_FULL_NAME, fullName)
        .claim(CLAIM_ROLES, roles != null ? roles : Collections.emptyList())
        .claim(CLAIM_PERMISSIONS, permissions != null ? permissions : Collections.emptyList())
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
  }

  // #endregion

  // #region validateToken
  public boolean validateToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      UUID.fromString(claims.getSubject());
      UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class));
      if (claims.getExpiration() == null) return false;
      for (String claim : List.of(CLAIM_ROLES, CLAIM_PERMISSIONS)) {
        Object value = claims.get(claim);
        if (value != null
            && (!(value instanceof List<?> list)
                || list.stream().anyMatch(item -> !(item instanceof String s) || s.isBlank()))) {
          return false;
        }
      }
      return true;
    } catch (ExpiredJwtException e) {
      log.warn("JWT token has expired: {}", e.getMessage());
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      log.warn("Invalid JWT token: {}", e.getMessage());
    }
    return false;
  }

  // #endregion

  // #region parseClaims
  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  // #endregion

  public UUID extractUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  public UUID extractTenantId(String token) {
    Object tid = parseClaims(token).get(CLAIM_TENANT_ID);
    return tid != null ? UUID.fromString(tid.toString()) : null;
  }

  public String extractEmail(String token) {
    return parseClaims(token).get(CLAIM_EMAIL, String.class);
  }

  public String extractFullName(String token) {
    return parseClaims(token).get(CLAIM_FULL_NAME, String.class);
  }

  @SuppressWarnings("unchecked")
  public List<String> extractRoles(String token) {
    Object roles = parseClaims(token).get(CLAIM_ROLES);
    return roles instanceof List<?> ? (List<String>) roles : Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  public List<String> extractPermissions(String token) {
    Object permissions = parseClaims(token).get(CLAIM_PERMISSIONS);
    return permissions instanceof List<?> ? (List<String>) permissions : Collections.emptyList();
  }

  public long getExpirationMs() {
    return expirationMs;
  }

  /** Session-backed IAM access token. A null tenant is valid only for identity/IAM APIs. */
  public String generateSessionAccessToken(
      UUID userId,
      UUID tenantId,
      String systemRole,
      UUID sessionId,
      List<String> roles,
      java.util.Set<String> permissions,
      java.time.Instant sessionExpiry) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(userId.toString())
        .issuer("iam-service")
        .claim("aud", "vishop-api")
        .claim("token_use", "access")
        .claim("sid", sessionId.toString())
        .claim("tenantId", tenantId == null ? null : tenantId.toString())
        .claim("systemRole", systemRole)
        .claim("roles", roles)
        .claim("permissions", permissions)
        .issuedAt(new Date(now))
        .expiration(new Date(Math.min(now + expirationMs, sessionExpiry.toEpochMilli())))
        .signWith(key)
        .compact();
  }
}
