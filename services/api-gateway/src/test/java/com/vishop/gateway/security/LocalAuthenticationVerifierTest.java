package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LocalAuthenticationVerifierTest {
  private static final String SECRET = "test-jwt-secret-with-at-least-thirty-two-bytes";
  private final io.github.coreplatform.common.test.security.IamTokenFixture tokens =
      new io.github.coreplatform.common.test.security.IamTokenFixture(new JwtTokenProvider(SECRET));
  private final AuthorizationCacheReader cache =
      org.mockito.Mockito.mock(AuthorizationCacheReader.class);
  private final LocalAuthenticationVerifier verifier =
      new LocalAuthenticationVerifier(tokens.provider(), cache);

  @org.junit.jupiter.api.BeforeEach
  void cacheState() {
    org.mockito.Mockito.when(cache.get(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            call -> {
              String key = call.getArgument(0);
              return reactor.core.publisher.Mono.just(
                  new io.github.coreplatform.common.security.gateway.AuthorizationSnapshot(
                      key, "1", "ACTIVE", "USER", role, "OWNER", Set.of("tenant.view"), 1));
            });
  }

  private final UUID role = UUID.randomUUID();
  private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();

  private String issue(io.github.coreplatform.common.test.security.IamTokenFixture provider) {
    return provider.generateIamAccessToken(
        user, tenant, SystemRole.USER.name(), "OWNER", Set.of("order.view"));
  }

  private void rejects(String token, boolean bearer) {
    StepVerifier.create(verifier.authenticate(token, bearer))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        AuthenticationFailure.class, f -> assertThat(f.status()).isEqualTo(401)))
        .verify();
  }

  @Test
  void resolvesCurrentPermissionsInsteadOfOldJwtGrants() {
    String jwt = issue(tokens);
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(
            c -> {
              assertThat(c.userId()).isEqualTo(user);
              assertThat(c.tenantId()).isEqualTo(tenant);
              assertThat(c.actor().permissions()).containsExactly("tenant.view");
            })
        .verifyComplete();
    assertThat(tokens.parseClaims(jwt)).doesNotContainKeys("sid", "sv", "v", "tv");
  }

  @Test
  void rejectsRefreshExpiredForgedAndNonBearerTokens() {
    rejects(tokens.generateRefreshToken(user, tenant, Instant.now().plusSeconds(1000)), true);
    rejects(
        issue(
            new io.github.coreplatform.common.test.security.IamTokenFixture(
                new JwtTokenProvider(SECRET), -1000)),
        true);
    rejects(
        issue(
            new io.github.coreplatform.common.test.security.IamTokenFixture(
                new JwtTokenProvider("another-signing-key-with-at-least-thirty-two-bytes"))),
        true);
    rejects(issue(tokens), false);
    rejects("invalid", true);
    rejects(null, true);
  }

  @Test
  void permitsIdentityJwtWithoutTenant() {
    var jwt = tokens.generateIamAccessToken(user, null, SystemRole.USER.name(), null, Set.of());
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(c -> assertThat(c.tenantId()).isNull())
        .verifyComplete();
  }

  @Test
  void rejectsRevokedUserVersionAndInactiveMembership() {
    var key = io.github.coreplatform.common.security.gateway.AuthorizationSnapshot.user(user);
    org.mockito.Mockito.when(cache.get(key))
        .thenReturn(
            Mono.just(
                new io.github.coreplatform.common.security.gateway.AuthorizationSnapshot(
                    key, "2", "ACTIVE", "USER", null, null, Set.of(), 2)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(
            e -> e instanceof AuthenticationFailure failure && failure.status() == 401)
        .verify();
    cacheState();
    key = io.github.coreplatform.common.security.gateway.AuthorizationSnapshot.member(tenant, user);
    org.mockito.Mockito.when(cache.get(key))
        .thenReturn(
            Mono.just(
                new io.github.coreplatform.common.security.gateway.AuthorizationSnapshot(
                    key, "2", "INACTIVE", null, role, null, Set.of(), 0)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(
            e -> e instanceof AuthenticationFailure failure && failure.status() == 403)
        .verify();
  }

  @Test
  void redisFailureDoesNotFallBackToJwtPermissions() {
    org.mockito.Mockito.when(cache.get(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Mono.error(new IllegalStateException("Redis down")));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
