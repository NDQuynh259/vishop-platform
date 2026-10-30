package com.vishop.gateway.security;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.jsonwebtoken.Claims;
import java.util.UUID;

/** Gateway's accepted IAM credential contract. Authorization grants come from Redis. */
record IamAccessTokenClaims(
    UUID userId,
    UUID tenantId,
    SystemRole systemRole,
    UUID tenantRoleId,
    String tenantRole,
    long tokenVersion,
    UUID sessionId) {
  static IamAccessTokenClaims from(Claims claims) {
    if (!"iam-service".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("vishop-api")
        || !"access".equals(claims.get("token_use"))
        || claims.getIssuedAt() == null
        || claims.getExpiration() == null)
      throw new IllegalArgumentException("Invalid IAM access credential");
    if (!(claims.get("tokenVersion") instanceof Number version) || version.longValue() < 1)
      throw new IllegalArgumentException("Invalid credential version");
    String tenant = claims.get("tenantId", String.class);
    String role = claims.get("tenantRoleId", String.class);
    String systemRole = claims.get("systemRole", String.class);
    if (!SystemRole.isValid(systemRole)) {
      throw new IllegalArgumentException("Invalid system role");
    }
    String tenantRole = claims.get("tenantRole", String.class);
    if (tenant == null && (role != null || tenantRole != null)) {
      throw new IllegalArgumentException("Role requires a selected tenant");
    }
    if (tenant != null
        && (role == null || role.isBlank() || tenantRole == null || tenantRole.isBlank())) {
      throw new IllegalArgumentException("Selected tenant requires a role ID and role key");
    }
    return new IamAccessTokenClaims(
        UUID.fromString(claims.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        SystemRole.valueOf(systemRole),
        role == null ? null : UUID.fromString(role),
        tenantRole,
        version.longValue(),
        UUID.fromString(claims.get("sid", String.class)));
  }
}
