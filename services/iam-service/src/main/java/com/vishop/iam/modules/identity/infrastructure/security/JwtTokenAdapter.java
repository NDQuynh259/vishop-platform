package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import com.vishop.iam.modules.identity.application.port.TokenPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JwtTokenAdapter implements TokenPort {
  private final JwtTokenProvider tokens;
  private final IamJwtTokenFactory factory;
  private final long refreshExpirationMs;
  private final EntityManager db;
  private final TenantAccessPort access;

  public JwtTokenAdapter(
      JwtTokenProvider tokens,
      IamJwtTokenFactory factory,
      EntityManager db,
      TenantAccessPort access,
      @Value("${vishop.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
    if (refreshExpirationMs <= factory.accessExpirationMs())
      throw new IllegalArgumentException("Refresh lifetime must exceed access lifetime");
    this.tokens = tokens;
    this.factory = factory;
    this.db = db;
    this.access = access;
    this.refreshExpirationMs = refreshExpirationMs;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public IamTokenClaims.AccessToken verifyAccessToken(String credential) {
    IamTokenClaims.AccessToken identity;
    try {
      identity = IamTokenClaimsParser.verifyAccessToken(tokens, credential);
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
    requireActiveUser(identity);
    if (identity.tenantId() != null
        && !access.hasActiveRole(
            identity.userId(),
            identity.tenantId(),
            identity.tenantRoleId(),
            identity.tenantRole())) {
      throw new UnauthorizedException("Tenant access is no longer valid");
    }
    return identity;
  }

  @Override
  public IamTokenClaims.RefreshToken verifyRefreshToken(String credential) {
    IamTokenClaims.RefreshToken identity;
    try {
      identity = IamTokenClaimsParser.verifyRefreshToken(tokens, credential);
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid refresh token");
    }
    requireCurrentVersion(identity.userId(), identity.tokenVersion());
    return identity;
  }

  @Override
  public String issue(AuthenticatedUser actor, UUID tenantRoleId) {
    return factory.access(actor, tenantRoleId, currentVersion(actor.userId()));
  }

  @Override
  public String issueRefresh(UUID userId, UUID tenantId, Instant expiresAt) {
    return factory.refresh(userId, tenantId, expiresAt, currentVersion(userId));
  }

  @Override
  public long expirationMs() {
    return factory.accessExpirationMs();
  }

  @Override
  public long refreshExpirationMs() {
    return refreshExpirationMs;
  }

  private void requireActiveUser(IamTokenClaims.AccessToken identity) {
    long matches =
        ((Number)
                db.createNativeQuery(
                        """
        SELECT count(*) FROM users
        WHERE id=:id AND status='ACTIVE' AND deleted_at IS NULL
          AND token_version=:version AND system_role=:systemRole
        """)
                    .setParameter("id", identity.userId())
                    .setParameter("version", identity.tokenVersion())
                    .setParameter("systemRole", identity.systemRole().name())
                    .getSingleResult())
            .longValue();
    if (matches != 1)
      throw new UnauthorizedException("Account is inactive or access token is revoked");
  }

  private void requireCurrentVersion(UUID userId, long tokenVersion) {
    if (tokenVersion != currentVersion(userId))
      throw new UnauthorizedException("Refresh token has been revoked");
  }

  private long currentVersion(UUID userId) {
    List<?> versions =
        db.createNativeQuery(
                """
        SELECT token_version FROM users
        WHERE id=:id AND status='ACTIVE' AND deleted_at IS NULL
        """)
            .setParameter("id", userId)
            .getResultList();
    if (versions.isEmpty()) throw new UnauthorizedException("Account is not active");
    return ((Number) versions.getFirst()).longValue();
  }
}
