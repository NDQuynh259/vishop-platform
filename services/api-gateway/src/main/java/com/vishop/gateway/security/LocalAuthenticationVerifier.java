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

  public Mono<Identity> authenticate(String credential, boolean bearer) {
    return Mono.fromCallable(
            () -> {
              try {
                if (!bearer || credential == null || credential.isBlank())
                  throw new IllegalArgumentException();
                return IamAccessTokenClaims.from(tokens.verify(credential));
              } catch (RuntimeException invalid) {
                throw new AuthenticationFailure(401, "unauthorized");
              }
            })
        .flatMap(
            c -> {
              UUID user = c.userId();
              UUID tenant = c.tenantId();
              return cache
                  .get(AuthorizationSnapshot.user(user))
                  .flatMap(
                      u -> {
                        active(u);
                        if (u.tokenVersion() != c.tokenVersion())
                          return Mono.error(new AuthenticationFailure(401, "token-revoked"));
                        if (tenant == null)
                          return Mono.just(
                              new Identity(
                                  user,
                                  null,
                                  null,
                                  new GatewayPrincipal(
                                      user, null, u.systemRole(), null, java.util.Set.of())));
                        return Mono.zip(
                                cache.get(AuthorizationSnapshot.tenant(tenant)),
                                cache.get(AuthorizationSnapshot.member(tenant, user)))
                            .flatMap(
                                pair -> {
                                  active(pair.getT1());
                                  active(pair.getT2());
                                  UUID role = pair.getT2().roleId();
                                  if (role == null)
                                    throw new AuthenticationFailure(403, "access-not-active");
                                  return cache
                                      .get(AuthorizationSnapshot.role(tenant, role))
                                      .map(
                                          r -> {
                                            active(r);
                                            if (!role.equals(r.roleId()))
                                              throw new IllegalStateException(
                                                  "Cached role mismatch");
                                            return new Identity(
                                                user,
                                                tenant,
                                                role,
                                                new GatewayPrincipal(
                                                    user,
                                                    tenant,
                                                    u.systemRole(),
                                                    r.roleKey(),
                                                    r.permissions()));
                                          });
                                });
                      });
            });
  }

  private void active(AuthorizationSnapshot state) {
    if (!"ACTIVE".equals(state.status())) throw new AuthenticationFailure(403, "access-not-active");
  }
}
