package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantGrant;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.domain.entity.AuthSession;
import com.vishop.iam.modules.identity.domain.entity.User;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Common lo HS256/time; IAM chỉ định issuer, audience và cấu trúc claims nghiệp vụ. */
@Component
public class JwtAuthTokens implements AuthTokens {
  private final JwtTokenService jwt;
  private final Duration accessLifetime;
  private final Duration refreshLifetime;

  public JwtAuthTokens(
      JwtTokenService jwt,
      @Value("${vishop.security.jwt.access-expiration-ms:900000}") long accessMs,
      @Value("${vishop.security.jwt.refresh-expiration-ms:604800000}") long refreshMs) {
    if (accessMs < 1000 || refreshMs <= accessMs)
      throw new IllegalArgumentException("Invalid token lifetimes");
    this.jwt = jwt;
    accessLifetime = Duration.ofMillis(accessMs);
    refreshLifetime = Duration.ofMillis(refreshMs);
  }

  public Duration refreshLifetime() {
    return refreshLifetime;
  }

  public Issued issue(
      User user, UUID tenant, TenantGrant grant, AuthSession session, String existingRefresh) {
    Instant deadline = session.expiresAt().truncatedTo(ChronoUnit.SECONDS);
    Instant accessEnd = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(accessLifetime);
    if (accessEnd.isAfter(deadline)) accessEnd = deadline;
    var access = base(user, tenant, session, "access", "vishop-api");
    access.put("systemRole", user.systemRole().name());
    access.put("tenantRoleId", grant == null ? null : grant.roleId().toString());
    access.put("tenantRole", grant == null ? null : grant.roleKey());
    // Không nhét permissions vào JWT: gateway luôn lấy grant mới nhất theo tenant + roleId.
    String accessToken = jwt.generateAccessToken(access, accessEnd);
    String refreshToken =
        existingRefresh == null
            ? jwt.generateRefreshToken(
                base(user, tenant, session, "refresh", "vishop-refresh"), deadline)
            : existingRefresh;
    return new Issued(accessToken, refreshToken, accessEnd, deadline);
  }

  public Access verifyAccess(String token) {
    Claims c = jwt.verifyAccessToken(token);
    Identity identity = identity(c, "access", "vishop-api");
    String role = c.get("tenantRole", String.class), id = c.get("tenantRoleId", String.class);
    if (identity.tenantId() == null
        ? role != null || id != null
        : role == null || role.isBlank() || id == null)
      throw new IllegalArgumentException("Tenant and role must be provided together");
    if (c.containsKey("roles")) throw new IllegalArgumentException("Legacy roles are not accepted");
    return new Access(
        identity,
        SystemRole.valueOf(c.get("systemRole", String.class)),
        id == null ? null : UUID.fromString(id),
        role);
  }

  public Refresh verifyRefresh(String token) {
    Claims c = jwt.verifyRefreshToken(token);
    return new Refresh(identity(c, "refresh", "vishop-refresh"), c.getExpiration().toInstant());
  }

  private Identity identity(Claims c, String purpose, String audience) {
    if (!"iam-service".equals(c.getIssuer())
        || c.getAudience() == null
        || !c.getAudience().contains(audience)
        || !purpose.equals(c.get("token_use"))
        || c.getId() == null) throw new IllegalArgumentException("Invalid IAM credential metadata");
    if (!(c.get("tokenVersion") instanceof Number version) || version.longValue() < 1)
      throw new IllegalArgumentException("Invalid token version");
    String tenant = c.get("tenantId", String.class);
    return new Identity(
        UUID.fromString(c.getSubject()),
        tenant == null ? null : UUID.fromString(tenant),
        UUID.fromString(c.get("sid", String.class)),
        version.longValue());
  }

  private Map<String, Object> base(
      User user, UUID tenant, AuthSession session, String purpose, String audience) {
    var c = new HashMap<String, Object>();
    c.put("sub", user.id().toString());
    c.put("iss", "iam-service");
    c.put("aud", List.of(audience));
    c.put("token_use", purpose);
    c.put("tenantId", tenant == null ? null : tenant.toString());
    // sid giữ nguyên suốt một lần đăng nhập; jti do common tạo riêng cho từng JWT.
    c.put("sid", session.id().toString());
    c.put("tokenVersion", user.tokenVersion());
    return c;
  }
}
