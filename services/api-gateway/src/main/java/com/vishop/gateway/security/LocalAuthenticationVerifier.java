package com.vishop.gateway.security;

import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.AuthorizationStatus;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Verifies the credential/session and resolves the current role; services load its permissions. */
@Component
public class LocalAuthenticationVerifier {
  public record Identity(UUID userId, UUID tenantId, UUID roleId, GatewayPrincipal actor) {}

  private final JwtTokenProvider tokens;
  private final AuthorizationCacheReader cache;

  public LocalAuthenticationVerifier(JwtTokenProvider tokens, AuthorizationCacheReader cache) {
    this.tokens = tokens;
    this.cache = cache;
  }

  record VerifiedAccessToken(IamAccessTokenClaims claims, AuthorizationSnapshot userState) {}

  public Mono<Identity> authenticate(String credential, boolean bearer) {
    if (!bearer) return Mono.error(new UnauthorizedException("unauthorized", "unauthorized"));
    return verifyAccessToken(credential).flatMap(this::resolveAuthorization);
  }

  /** Verifies the JWT and compares its credential version with the cached user version. */
  Mono<VerifiedAccessToken> verifyAccessToken(String credential) {
    return Mono.fromCallable(
            () -> {
              try {
                if (credential == null || credential.isBlank())
                  throw new IllegalArgumentException();
                return IamAccessTokenClaims.from(tokens.verify(credential));
              } catch (RuntimeException invalid) {
                throw new UnauthorizedException("unauthorized", "unauthorized");
              }
            })
        .flatMap(
            claims -> {
              String key = AuthorizationCacheKeys.user(claims.userId());
              return snapshot(key)
                  .flatMap(
                      state -> {
                        validUserState(state);
                        return claims.tokenVersion() > state.tokenVersion()
                            ? checkedSnapshot(key, cache.refresh(key))
                            : Mono.just(state);
                      })
                  .map(
                      state -> {
                        validUserState(state);
                        if (state.status() != AuthorizationStatus.ACTIVE)
                          throw new ForbiddenException("access-not-active", "access-not-active");
                        if (state.tokenVersion() < claims.tokenVersion()) {
                          throw new IllegalStateException(
                              "Credential version is ahead of confirmed user state");
                        }
                        if (state.tokenVersion() != claims.tokenVersion())
                          throw new UnauthorizedException("token-revoked", "token-revoked");
                        return new VerifiedAccessToken(claims, state);
                      });
            });
  }

  private Mono<Identity> resolveAuthorization(VerifiedAccessToken verified) {
    var claims = verified.claims();
    // tokenVersion đúng chưa đủ: logout chỉ thu hồi sid, không tăng version toàn user.
    // Kiểm tra chủ sở hữu và hạn phiên trước khi đọc quyền của tenant.
    return snapshot(AuthorizationCacheKeys.session(claims.sessionId()))
        .flatMap(
            session -> {
              if (session.status() != AuthorizationStatus.ACTIVE)
                return Mono.error(new UnauthorizedException("session-revoked", "session-revoked"));
              if (session.expiresAt() == null)
                return Mono.error(new IllegalStateException("Cached session expiry is missing"));
              if (!session.expiresAt().isAfter(java.time.Instant.now()))
                return Mono.error(new UnauthorizedException("session-expired", "session-expired"));
              if (!claims.userId().equals(session.userId())
                  || session.tokenVersion() != claims.tokenVersion())
                return Mono.error(new UnauthorizedException("invalid-session", "invalid-session"));
              return resolveTenant(verified);
            });
  }

  private Mono<Identity> resolveTenant(VerifiedAccessToken verified) {
    var claims = verified.claims();
    var userState = verified.userState();
    UUID user = claims.userId();
    UUID tenant = claims.tenantId();
    if (tenant == null) {
      return Mono.just(
          new Identity(
              user,
              null,
              null,
              new GatewayPrincipal(
                  user,
                  null,
                  userState.systemRole(),
                  null,
                  java.util.Set.of(),
                  claims.sessionId())));
    }
    // Resolve the current role from membership, never from stale JWT role claims.
    return Mono.zip(
            snapshot(AuthorizationCacheKeys.tenant(tenant)),
            snapshot(AuthorizationCacheKeys.member(tenant, user)))
        .flatMap(
            pair -> {
              active(pair.getT1());
              var membership = pair.getT2();
              if (!user.equals(membership.userId()))
                return Mono.error(new IllegalStateException("Cached membership mismatch"));
              if (membership.status() != AuthorizationStatus.ACTIVE)
                return Mono.error(
                    new ForbiddenException("membership-inactive", "Membership is not active"));
              UUID role = membership.roleId();
              if (role == null)
                return Mono.error(new IllegalStateException("Membership role is missing"));
              String roleKey = AuthorizationCacheKeys.role(tenant, role);
              return snapshot(roleKey)
                  .map(
                      grant -> {
                        active(grant);
                        if (!role.equals(grant.roleId()))
                          throw new IllegalStateException("Cached role mismatch");
                        if (grant.roleKey() == null || grant.roleKey().isBlank())
                          throw new IllegalStateException("Cached role key is missing");
                        return new Identity(
                            user,
                            tenant,
                            role,
                            new GatewayPrincipal(
                                user,
                                tenant,
                                userState.systemRole(),
                                grant.roleKey(),
                                java.util.Set.of(),
                                claims.sessionId()));
                      });
            });
  }

  private void active(AuthorizationSnapshot state) {
    if (state.status() != AuthorizationStatus.ACTIVE)
      throw new ForbiddenException("access-not-active", "access-not-active");
  }

  private Mono<AuthorizationSnapshot> snapshot(String key) {
    return checkedSnapshot(key, cache.get(key));
  }

  private Mono<AuthorizationSnapshot> checkedSnapshot(
      String key, Mono<AuthorizationSnapshot> source) {
    return source
        .switchIfEmpty(Mono.error(new IllegalStateException("Authorization snapshot is missing")))
        .map(
            state -> {
              if (!key.equals(state.key()))
                throw new IllegalStateException("Cached authorization key mismatch");
              return state;
            });
  }

  private void validUserState(AuthorizationSnapshot state) {
    // Denial tombstones may omit account details; only active accounts need a full profile.
    if (state.status() != AuthorizationStatus.ACTIVE) return;
    if (state.tokenVersion() < 1)
      throw new IllegalStateException("Cached user token version is invalid");
    if (!SystemRole.isValid(state.systemRole()))
      throw new IllegalStateException("Cached system role is invalid");
  }
}
