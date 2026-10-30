package com.vishop.gateway.security;

import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Verifies the user credential once, then resolves current tenant permissions from Redis. */
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
    if (!bearer) return Mono.error(new AuthenticationFailure(401, "unauthorized"));
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
                throw new AuthenticationFailure(401, "unauthorized");
              }
            })
        .flatMap(
            claims -> {
              String key = AuthorizationSnapshot.user(claims.userId());
              return cache
                  .get(key)
                  .flatMap(
                      state ->
                          claims.tokenVersion() > state.tokenVersion()
                              ? cache.refresh(key)
                              : Mono.just(state))
                  .map(
                      state -> {
                        if (state.tokenVersion() < claims.tokenVersion()) {
                          throw new IllegalStateException(
                              "Credential version is ahead of confirmed user state");
                        }
                        if (state.tokenVersion() != claims.tokenVersion())
                          throw new AuthenticationFailure(401, "token-revoked");
                        return new VerifiedAccessToken(claims, state);
                      });
            });
  }

  private Mono<Identity> resolveAuthorization(VerifiedAccessToken verified) {
    var claims = verified.claims();
    var userState = verified.userState();
    active(userState);
    UUID user = claims.userId();
    UUID tenant = claims.tenantId();
    if (tenant == null) {
      return Mono.just(
          new Identity(
              user,
              null,
              null,
              new GatewayPrincipal(user, null, userState.systemRole(), null, java.util.Set.of())));
    }
    UUID role = claims.tenantRoleId();
    return Mono.zip(
            cache.get(AuthorizationSnapshot.tenant(tenant)),
            cache.get(AuthorizationSnapshot.role(tenant, role)))
        .map(
            pair -> {
              active(pair.getT1());
              var grant = pair.getT2();
              active(grant);
              String roleKey = AuthorizationSnapshot.role(tenant, role);
              if (!roleKey.equals(grant.key()) || !role.equals(grant.roleId())) {
                throw new IllegalStateException("Cached role mismatch");
              }
              return new Identity(
                  user,
                  tenant,
                  role,
                  new GatewayPrincipal(
                      user, tenant, userState.systemRole(), grant.roleKey(), grant.permissions()));
            });
  }

  private void active(AuthorizationSnapshot state) {
    if (!"ACTIVE".equals(state.status())) throw new AuthenticationFailure(403, "access-not-active");
  }
}
