package com.vishop.iam.modules.identity.infrastructure.security;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validated IAM access claims: one system role and one role in the selected tenant. */
public record IamAccessTokenClaims(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
  public static IamAccessTokenClaims verify(JwtTokenProvider provider, String token) {
    return fromClaims(provider.verify(token));
  }

  /** Maps claims after the JWT parser has verified the signature and token lifetime. */
  public static IamAccessTokenClaims fromClaims(Claims claims) {
    validateAccessClaims(claims);

    String systemRole = readSystemRole(claims);
    if (claims.containsKey("roles")) {
      throw new IllegalArgumentException("Legacy roles claim is not supported");
    }

    String tenantId = claims.get("tenantId", String.class);
    String tenantRole = claims.get("tenantRole", String.class);
    validateTenantRole(tenantId, tenantRole);

    return new IamAccessTokenClaims(
        UUID.fromString(claims.getSubject()),
        tenantId == null ? null : UUID.fromString(tenantId),
        systemRole,
        tenantRole,
        readPermissions(claims));
  }

  private static void validateAccessClaims(Claims claims) {
    boolean issuedByIam = "iam-service".equals(claims.getIssuer());
    boolean intendedForApi =
        claims.getAudience() != null && claims.getAudience().contains("vishop-api");
    boolean accessToken = "access".equals(claims.get("token_use"));
    boolean hasLifetime = claims.getIssuedAt() != null && claims.getExpiration() != null;

    if (!issuedByIam || !intendedForApi || !accessToken || !hasLifetime) {
      throw new IllegalArgumentException("Invalid access claims");
    }
  }

  private static String readSystemRole(Claims claims) {
    String systemRole = claims.get("systemRole", String.class);
    if (!SystemRole.isValid(systemRole)) {
      throw new IllegalArgumentException("Invalid system role");
    }
    return systemRole;
  }

  private static void validateTenantRole(String tenantId, String tenantRole) {
    if (tenantId == null) {
      if (tenantRole != null) {
        throw new IllegalArgumentException("Tenant role must match the selected tenant");
      }
      return;
    }

    if (tenantRole == null || tenantRole.isBlank()) {
      throw new IllegalArgumentException("Tenant role must match the selected tenant");
    }
  }

  private static Set<String> readPermissions(Claims claims) {
    if (!(claims.get("permissions") instanceof List<?> values)) {
      throw new IllegalArgumentException("Invalid permissions");
    }

    var permissions = new HashSet<String>();
    for (Object value : values) {
      if (!(value instanceof String permission) || permission.isBlank()) {
        throw new IllegalArgumentException("Invalid permissions");
      }
      permissions.add(permission);
    }
    return Set.copyOf(permissions);
  }
}
