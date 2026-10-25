package io.github.coreplatform.common.security.jwt;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validated stateless IAM access-token claims. */
public record VerifiedAccessToken(
    UUID userId, UUID tenantId, String systemRole, List<String> roles, Set<String> permissions) {
  public static VerifiedAccessToken verify(JwtTokenProvider provider, String token) {
    Claims c = provider.parseClaims(token);
    if (!"iam-service".equals(c.getIssuer())
        || c.getAudience() == null
        || !c.getAudience().contains("vishop-api")
        || !"access".equals(c.get("token_use"))
        || c.getExpiration() == null
        || c.getIssuedAt() == null) throw new IllegalArgumentException("Invalid access claims");

    String systemRole = c.get("systemRole", String.class);
    if (!Set.of("USER", "PLATFORM_ADMIN").contains(systemRole))
      throw new IllegalArgumentException("Invalid system role");

    for (String name : List.of("roles", "permissions")) {
      if (!(c.get(name) instanceof List<?> values)
          || values.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank()))
        throw new IllegalArgumentException("Invalid grants");
    }
    String tenant = c.get("tenantId", String.class);
    return new VerifiedAccessToken(
        UUID.fromString(c.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        systemRole,
        strings(c, "roles"),
        Set.copyOf(strings(c, "permissions")));
  }

  private static List<String> strings(Claims claims, String key) {
    return ((List<?>) claims.get(key)).stream().map(String.class::cast).toList();
  }
}
