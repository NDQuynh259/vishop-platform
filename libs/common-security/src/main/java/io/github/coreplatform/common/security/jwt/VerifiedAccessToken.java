package io.github.coreplatform.common.security.jwt;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validated IAM access claims: one system role and one role in the selected tenant. */
public record VerifiedAccessToken(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
  public static VerifiedAccessToken verify(JwtTokenProvider provider, String token) {
    Claims c = provider.parseClaims(token);
    if (!"iam-service".equals(c.getIssuer())
        || c.getAudience() == null
        || !c.getAudience().contains("vishop-api")
        || !"access".equals(c.get("token_use"))
        || c.getExpiration() == null
        || c.getIssuedAt() == null) throw new IllegalArgumentException("Invalid access claims");

    String systemRole = c.get("systemRole", String.class);
    if (!SystemRole.isValid(systemRole)) throw new IllegalArgumentException("Invalid system role");
    if (c.containsKey("roles"))
      throw new IllegalArgumentException("Legacy roles claim is not supported");
    String tenant = c.get(JwtTokenProvider.CLAIM_TENANT_ID, String.class);
    String tenantRole = c.get(JwtTokenProvider.CLAIM_TENANT_ROLE, String.class);
    if (tenant == null ? tenantRole != null : tenantRole == null || tenantRole.isBlank())
      throw new IllegalArgumentException("Tenant role must match the selected tenant");
    if (!(c.get("permissions") instanceof List<?> permissions)
        || permissions.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank()))
      throw new IllegalArgumentException("Invalid permissions");
    return new VerifiedAccessToken(
        UUID.fromString(c.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        systemRole,
        tenantRole,
        Set.copyOf(permissions.stream().map(String.class::cast).toList()));
  }
}
