package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LocalAuthenticationVerifierTest {
  private static final String SECRET = "test-jwt-secret-with-at-least-thirty-two-bytes";
  private final JwtTokenProvider tokens = new JwtTokenProvider(SECRET, 900000);
  private final LocalAuthenticationVerifier verifier = new LocalAuthenticationVerifier(tokens);
  private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();

  private String issue(JwtTokenProvider provider) {
    return provider.generateIamAccessToken(
        user, tenant, "USER", List.of("OWNER"), Set.of("order.view"));
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
  void verifiesWithoutAnyRemoteState() {
    String jwt = issue(tokens);
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(
            c -> {
              assertThat(c.userId()).isEqualTo(user);
              assertThat(c.tenantId()).isEqualTo(tenant);
              assertThat(c.accessToken()).isEqualTo(jwt);
            })
        .verifyComplete();
    assertThat(tokens.parseClaims(jwt)).doesNotContainKeys("sid", "sv", "v", "tv");
  }

  @Test
  void rejectsRefreshExpiredForgedAndNonBearerTokens() {
    rejects(tokens.generateRefreshToken(user, tenant, Instant.now().plusSeconds(1000)), true);
    rejects(issue(new JwtTokenProvider(SECRET, -1000)), true);
    rejects(
        issue(new JwtTokenProvider("another-signing-key-with-at-least-thirty-two-bytes", 900000)),
        true);
    rejects(issue(tokens), false);
    rejects("invalid", true);
    rejects(null, true);
  }

  @Test
  void permitsIdentityJwtWithoutTenant() {
    var jwt = tokens.generateIamAccessToken(user, null, "USER", List.of(), Set.of());
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(c -> assertThat(c.tenantId()).isNull())
        .verifyComplete();
  }
}
