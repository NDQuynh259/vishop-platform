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
 * Utility component for generating, signing, parsing, and validating JWT access tokens.
 * Shared across identity-service (token issuance) and api-gateway (token mediation).
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
            @Value("${vishop.security.jwt.secret:vishop-platform-super-secret-key-that-is-at-least-256-bits-long}")
            String secret,
            @Value("${vishop.security.jwt.expiration-ms:86400000}")
            long expirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
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
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }
    // #endregion

    // #region parseClaims
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
}
