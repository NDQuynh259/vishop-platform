package com.vishop.iam.modules.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IamTokenClaimsTest {
  private static final String SECRET = "test-signing-secret-with-at-least-thirty-two-bytes";
  private final io.github.coreplatform.common.test.security.IamTokenFixture tokens =
      new io.github.coreplatform.common.test.security.IamTokenFixture(new JwtTokenProvider(SECRET));
  private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();

  @Test
  void separatesSystemRoleAndSingleTenantRole() {
    String jwt =
        tokens.generateIamAccessToken(
            user, tenant, SystemRole.USER.name(), "OWNER", Set.of("order.view"));
    var access = IamTokenClaimsParser.verifyAccessToken(tokens.provider(), jwt);
    assertThat(access.systemRole()).isEqualTo(SystemRole.USER);
    assertThat(access.tenantRole()).isEqualTo("OWNER");
    assertThat(access.tenantRoleId()).isNotNull();
    assertThat(access.tokenVersion()).isEqualTo(1);
    assertThat(tokens.parseClaims(jwt))
        .containsEntry("tenantRole", "OWNER")
        .doesNotContainKey("roles");
    var global =
        IamTokenClaimsParser.verifyAccessToken(
            tokens.provider(),
            tokens.generateIamAccessToken(
                user, null, SystemRole.PLATFORM_ADMIN.name(), null, Set.of()));
    assertThat(global.tenantId()).isNull();
    assertThat(global.tenantRole()).isNull();
  }

  @Test
  void rejectsLegacyListsWrongTypesAndMissingSelectedTenantRole() {
    String valid =
        tokens.generateIamAccessToken(user, tenant, SystemRole.USER.name(), "OWNER", Set.of());
    for (Object role : List.of(List.of("OWNER", "ADMIN"), 42, "", " ")) {
      var claims = new HashMap<String, Object>(tokens.parseClaims(valid));
      claims.put("tenantRole", role);
      assertRejected(sign(claims));
    }
    var legacy = new HashMap<String, Object>(tokens.parseClaims(valid));
    legacy.remove("tenantRole");
    legacy.put("roles", List.of("OWNER"));
    assertRejected(sign(legacy));
    assertRejected(
        tokens.generateIamAccessToken(user, tenant, SystemRole.USER.name(), null, Set.of()));
    assertRejected(
        tokens.generateIamAccessToken(user, null, SystemRole.USER.name(), "OWNER", Set.of()));
  }

  @Test
  void acceptsPermissionFreeClaimsAndRejectsMissingRoleIdentityOrVersion() {
    var claims =
        new HashMap<String, Object>(
            tokens.parseClaims(
                tokens.generateIamAccessToken(
                    user, tenant, "USER", "OWNER", Set.of("order.view"))));
    claims.remove("permissions");
    assertThat(IamTokenClaimsParser.verifyAccessToken(tokens.provider(), sign(claims)).tenantId())
        .isEqualTo(tenant);
    for (String field : List.of("tenantRoleId", "tokenVersion")) {
      var missing = new HashMap<String, Object>(claims);
      missing.remove(field);
      assertRejected(sign(missing));
    }
    claims.put("tenantRoleId", "invalid-uuid");
    assertRejected(sign(claims));
  }

  @Test
  void readsRefreshIdentityAndRejectsUsingTheWrongTokenPurpose() {
    var expiry =
        java.time.Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    String refreshToken = tokens.generateRefreshToken(user, tenant, expiry);
    var refresh = IamTokenClaimsParser.verifyRefreshToken(tokens.provider(), refreshToken);
    assertThat(refresh.userId()).isEqualTo(user);
    assertThat(refresh.tenantId()).isEqualTo(tenant);
    assertThat(refresh.tokenVersion()).isEqualTo(1);
    assertThat(refresh.expiresAt()).isEqualTo(expiry);
    assertThatThrownBy(
            () -> IamTokenClaimsParser.verifyAccessToken(tokens.provider(), refreshToken))
        .isInstanceOf(IllegalArgumentException.class);
    String accessToken = tokens.generateIamAccessToken(user, tenant, "USER", "OWNER", Set.of());
    assertThatThrownBy(
            () -> IamTokenClaimsParser.verifyRefreshToken(tokens.provider(), accessToken))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refreshRequiresAllCommonMetadataAndItsJwtId() {
    var valid =
        tokens.parseClaims(
            tokens.generateRefreshToken(user, tenant, java.time.Instant.now().plusSeconds(600)));
    for (String field : List.of("iss", "aud", "iat", "exp", "jti", "tokenVersion")) {
      var missing = new HashMap<String, Object>(valid);
      missing.remove(field);
      assertThatThrownBy(
              () -> IamTokenClaimsParser.verifyRefreshToken(tokens.provider(), sign(missing)))
          .as("Missing refresh claim: %s", field)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private String sign(java.util.Map<String, Object> claims) {
    return Jwts.builder()
        .claims(claims)
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  private void assertRejected(String jwt) {
    assertThatThrownBy(() -> IamTokenClaimsParser.verifyAccessToken(tokens.provider(), jwt))
        .isInstanceOf(RuntimeException.class);
  }
}
