package com.vishop.iam.modules.identity.infrastructure.security;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.modules.identity.application.port.TokenPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
  public AuthenticatedUser verify(String credential) {
    try {
      var identity = IamAccessTokenClaims.verify(tokens, credential);
      requireCurrentVersion(identity.userId(), identity.tokenVersion());
      return new AuthenticatedUser(
          identity.userId(),
          identity.tenantId(),
          identity.systemRole(),
          identity.tenantRole(),
          currentPermissions(identity));
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid access token");
    }
  }

  @Override
  public RefreshIdentity verifyRefresh(String credential) {
    try {
      var identity =
          IamTokenClaims.fromClaims(tokens.verify(credential), IamTokenClaims.Purpose.REFRESH);
      requireCurrentVersion(identity.userId(), identity.tokenVersion());
      return new RefreshIdentity(identity.userId(), identity.tenantId(), identity.expiresAt());
    } catch (RuntimeException invalid) {
      throw new UnauthorizedException("Invalid refresh token");
    }
  }

  @Override
  public String issue(AuthenticatedUser actor, UUID roleId) {
    return factory.access(actor, roleId, currentVersion(actor.userId()));
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

  private Set<String> currentPermissions(IamAccessTokenClaims identity) {
    if (identity.tenantId() == null) return Set.of();
    var grant = access.activeGrant(identity.userId(), identity.tenantId()).orElseThrow();
    if (!grant.roleId().equals(identity.roleId()) || !grant.roleKey().equals(identity.tenantRole()))
      throw new IllegalArgumentException("Stale tenant role");
    return grant.permissions();
  }

  private void requireCurrentVersion(UUID userId, long tokenVersion) {
    if (tokenVersion != currentVersion(userId))
      throw new IllegalArgumentException("Revoked credential");
  }

  private long currentVersion(UUID userId) {
    return ((Number)
            db.createNativeQuery("SELECT token_version FROM users WHERE id=:id")
                .setParameter("id", userId)
                .getSingleResult())
        .longValue();
  }
}
