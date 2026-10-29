package io.github.coreplatform.common.test.security;

import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.*;

/** Test-only IAM credential examples; no production service depends on this policy. */
public final class IamTokenFixture {
  private final JwtTokenProvider provider;
  private final long accessExpirationMs;

  public IamTokenFixture(JwtTokenProvider provider) {
    this(provider, 900000);
  }

  public IamTokenFixture(JwtTokenProvider provider, long accessExpirationMs) {
    this.provider = provider;
    this.accessExpirationMs = accessExpirationMs;
  }

  public JwtTokenProvider provider() {
    return provider;
  }

  public Claims parseClaims(String token) {
    return provider.verify(token);
  }

  public String generateIamAccessToken(
      UUID user, UUID tenant, String systemRole, String tenantRole, Set<String> permissions) {
    var claims =
        base(user, tenant, "access", "vishop-api", Instant.now().plusMillis(accessExpirationMs));
    claims.put("systemRole", systemRole);
    claims.put("tenantRole", tenantRole);
    claims.put("permissions", permissions);
    return provider.encode(claims);
  }

  public String generateRefreshToken(UUID user, UUID tenant, Instant expiry) {
    return provider.encode(base(user, tenant, "refresh", "vishop-refresh", expiry));
  }

  public GatewayPrincipal access(String token) {
    var claims = provider.verify(token);
    if (!"iam-service".equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains("vishop-api")
        || !"access".equals(claims.get("token_use")))
      throw new IllegalArgumentException("Invalid test credential");
    String tenant = claims.get("tenantId", String.class);
    var permissions = (List<?>) claims.get("permissions");
    return new GatewayPrincipal(
        UUID.fromString(claims.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        claims.get("systemRole", String.class),
        claims.get("tenantRole", String.class),
        Set.copyOf(permissions.stream().map(String.class::cast).toList()));
  }

  private Map<String, Object> base(
      UUID user, UUID tenant, String use, String audience, Instant expiry) {
    var claims = new HashMap<String, Object>();
    claims.put("sub", user.toString());
    claims.put("tenantId", tenant == null ? null : tenant.toString());
    claims.put("iss", "iam-service");
    claims.put("aud", List.of(audience));
    claims.put("token_use", use);
    claims.put("iat", Date.from(Instant.now()));
    claims.put("exp", Date.from(expiry));
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("tokenVersion", 1);
    return claims;
  }
}
