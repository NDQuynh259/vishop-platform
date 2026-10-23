package io.github.coreplatform.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  @Test
  void rejectsMissingSecret() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new JwtTokenProvider("", 3600000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSignedTokensWithoutTenantOrExpiration() {
    var key =
        io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String noTenant =
        io.jsonwebtoken.Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
            .signWith(key)
            .compact();
    String noExpiration =
        io.jsonwebtoken.Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("tenantId", UUID.randomUUID().toString())
            .signWith(key)
            .compact();
    assertThat(jwtTokenProvider.validateToken(noTenant)).isFalse();
    assertThat(jwtTokenProvider.validateToken(noExpiration)).isFalse();
  }

  private JwtTokenProvider jwtTokenProvider;
  private final String secret = "vishop-platform-super-secret-key-that-is-at-least-256-bits-long";

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider(secret, 3600000L); // 1 hour
  }

  @Test
  @DisplayName("Should generate valid token and extract claims correctly")
  void shouldGenerateAndExtractClaims() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    String email = "admin@vishop.vn";
    String fullName = "System Admin";
    List<String> roles = List.of("OWNER", "ADMIN");
    List<String> permissions = List.of("tenant.view", "tenant.create");

    String token =
        jwtTokenProvider.generateAccessToken(userId, tenantId, email, fullName, roles, permissions);

    assertThat(token).isNotBlank();
    assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
    assertThat(jwtTokenProvider.extractTenantId(token)).isEqualTo(tenantId);
    assertThat(jwtTokenProvider.extractEmail(token)).isEqualTo(email);
    assertThat(jwtTokenProvider.extractFullName(token)).isEqualTo(fullName);
    assertThat(jwtTokenProvider.extractRoles(token)).containsExactlyElementsOf(roles);
    assertThat(jwtTokenProvider.extractPermissions(token)).containsExactlyElementsOf(permissions);
  }

  @Test
  @DisplayName("Should reject invalid or expired token")
  void shouldRejectInvalidToken() {
    assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    assertThat(jwtTokenProvider.validateToken("")).isFalse();
    assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();

    // Expired token
    JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secret, -1000L); // expired 1s ago
    String expiredToken =
        shortLivedProvider.generateAccessToken(
            UUID.randomUUID(), UUID.randomUUID(), "test@vishop.vn", "Test", List.of(), List.of());
    assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
  }
}
