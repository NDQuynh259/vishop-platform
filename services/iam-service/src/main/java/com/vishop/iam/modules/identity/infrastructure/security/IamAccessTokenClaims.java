package com.vishop.iam.modules.identity.infrastructure.security;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.UUID;

/** Validated IAM access claims: one system role and one role in the selected tenant. */
public record IamAccessTokenClaims(
    UUID userId,
    UUID tenantId,
    UUID roleId,
    String systemRole,
    String tenantRole,
    long tokenVersion) {
  public IamAccessTokenClaims {
    java.util.Objects.requireNonNull(userId);
    if (!SystemRole.isValid(systemRole)) throw new IllegalArgumentException("Invalid system role");
    if (tokenVersion < 1) throw new IllegalArgumentException("Invalid credential version");
    if (tenantId == null
        ? roleId != null || tenantRole != null
        : roleId == null || tenantRole == null || tenantRole.isBlank())
      throw new IllegalArgumentException("Role must match the selected tenant");
  }

  public static IamAccessTokenClaims verify(JwtTokenProvider provider, String token) {
    return fromClaims(provider.verify(token));
  }

  /** Maps claims after the JWT parser has verified the signature and token lifetime. */
  public static IamAccessTokenClaims fromClaims(Claims claims) {
    var identity = IamTokenClaims.fromClaims(claims, IamTokenClaims.Purpose.ACCESS);
    if (claims.containsKey("roles")) {
      throw new IllegalArgumentException("Legacy roles claim is not supported");
    }

    String roleId = claims.get("roleId", String.class);

    return new IamAccessTokenClaims(
        identity.userId(),
        identity.tenantId(),
        roleId == null ? null : UUID.fromString(roleId),
        claims.get("systemRole", String.class),
        claims.get("tenantRole", String.class),
        identity.tokenVersion());
  }
}
