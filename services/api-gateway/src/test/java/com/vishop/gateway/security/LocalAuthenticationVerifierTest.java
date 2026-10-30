package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import io.github.coreplatform.common.test.security.IamTokenFixture;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LocalAuthenticationVerifierTest {
  private static final String SECRET = "test-jwt-secret-with-at-least-thirty-two-bytes";
  private final IamTokenFixture tokens =
      new IamTokenFixture(
          new JwtTokenProvider(SECRET),
          new JwtTokenProvider("test-gateway-refresh-key-is-different-and-at-least-32-bytes"));
  private final AuthorizationCacheReader cache = mock(AuthorizationCacheReader.class);
  private final LocalAuthenticationVerifier verifier =
      new LocalAuthenticationVerifier(tokens.provider(), cache);
  private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();
  private final UUID role =
      UUID.nameUUIDFromBytes((tenant + ":OWNER").getBytes(StandardCharsets.UTF_8));

  @BeforeEach
  void cacheState() {
    when(cache.get(anyString()))
        .thenAnswer(
            call ->
                Mono.just(
                    new AuthorizationSnapshot(
                        call.getArgument(0),
                        "1",
                        "ACTIVE",
                        "USER",
                        role,
                        "OWNER",
                        Set.of("tenant.view"),
                        1,
                        user,
                        Instant.now().plusSeconds(604800))));
  }

  private String issue(IamTokenFixture fixture) {
    return fixture.generateIamAccessToken(
        user, tenant, SystemRole.USER.name(), "OWNER", Set.of("order.view"));
  }

  private void rejects(String token, boolean bearer) {
    StepVerifier.create(verifier.authenticate(token, bearer))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        AuthenticationFailure.class, f -> assertThat(f.status()).isEqualTo(401)))
        .verify();
  }

  @Test
  void verifiesAccessTokenUsingOnlyUserVersionCache() {
    StepVerifier.create(verifier.verifyAccessToken(issue(tokens)))
        .assertNext(
            verified -> {
              assertThat(verified.claims().userId()).isEqualTo(user);
              assertThat(verified.claims().tenantId()).isEqualTo(tenant);
              assertThat(verified.claims().systemRole()).isEqualTo(SystemRole.USER);
              assertThat(verified.claims().tenantRoleId()).isEqualTo(role);
              assertThat(verified.claims().tenantRole()).isEqualTo("OWNER");
              assertThat(verified.claims().tokenVersion()).isEqualTo(1);
            })
        .verifyComplete();
    verify(cache).get(AuthorizationSnapshot.user(user));
    verifyNoMoreInteractions(cache);
  }

  @Test
  void directAccessTokenVerificationRejectsRevokedVersion() {
    String key = AuthorizationSnapshot.user(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(key, "2", "ACTIVE", "USER", null, null, Set.of(), 2)));
    StepVerifier.create(verifier.verifyAccessToken(issue(tokens)))
        .expectErrorMatches(
            error ->
                error instanceof AuthenticationFailure failure
                    && failure.status() == 401
                    && "token-revoked".equals(failure.code()))
        .verify();
    verify(cache).get(key);
    verifyNoMoreInteractions(cache);
  }

  @Test
  void usesJwtRoleAfterVersionAndSessionChecks() {
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .assertNext(
            identity -> {
              assertThat(identity.userId()).isEqualTo(user);
              assertThat(identity.tenantId()).isEqualTo(tenant);
              assertThat(identity.roleId()).isEqualTo(role);
              assertThat(identity.actor().permissions()).containsExactly("tenant.view");
            })
        .verifyComplete();
    verify(cache).get(AuthorizationSnapshot.user(user));
    verify(cache).get(AuthorizationSnapshot.session(user));
    verify(cache).get(AuthorizationSnapshot.tenant(tenant));
    verify(cache).get(AuthorizationSnapshot.role(tenant, role));
    verifyNoMoreInteractions(cache);
  }

  @Test
  void rejectsMissingSessionIdBeforeReadingCache() {
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.remove("sid");
    rejects(tokens.provider().encode(claims), true);
    verifyNoInteractions(cache);
  }

  @ParameterizedTest
  @ValueSource(strings = {"revoked", "expired", "wrong-user", "wrong-version"})
  void rejectsInvalidSessionBeforeUsingTenantPermissions(String kind) {
    String key = AuthorizationSnapshot.session(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "2",
                    kind.equals("revoked") ? "REVOKED" : "ACTIVE",
                    null,
                    null,
                    null,
                    Set.of(),
                    kind.equals("wrong-version") ? 2 : 1,
                    kind.equals("wrong-user") ? UUID.randomUUID() : user,
                    Instant.now().plusSeconds(kind.equals("expired") ? -1 : 3600))));
    rejects(issue(tokens), true);
    verify(cache, never()).get(AuthorizationSnapshot.tenant(tenant));
    verify(cache, never()).get(AuthorizationSnapshot.role(tenant, role));
  }

  @Test
  void permissionEditsApplyToSameCredentialWithoutRevocationOrRefresh() {
    String jwt = issue(tokens);
    String key = AuthorizationSnapshot.role(tenant, role);
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(
            identity -> assertThat(identity.actor().permissions()).containsExactly("tenant.view"))
        .verifyComplete();
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "2", "ACTIVE", null, role, "OWNER", Set.of("inventory.view"), 0)));
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(
            identity ->
                assertThat(identity.actor().permissions()).containsExactly("inventory.view"))
        .verifyComplete();
  }

  @Test
  void rejectsRevokedVersionBeforeUsingJwtRole() {
    String key = AuthorizationSnapshot.user(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(key, "2", "ACTIVE", "USER", null, null, Set.of(), 2)));
    rejects(issue(tokens), true);
    verify(cache, never()).get(AuthorizationSnapshot.tenant(tenant));
    verify(cache, never()).get(AuthorizationSnapshot.role(tenant, role));
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "tenant", "role"})
  void deniesInactiveUserTenantOrRole(String kind) {
    String key =
        switch (kind) {
          case "user" -> AuthorizationSnapshot.user(user);
          case "tenant" -> AuthorizationSnapshot.tenant(tenant);
          default -> AuthorizationSnapshot.role(tenant, role);
        };
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "2", "INACTIVE", "USER", role, "OWNER", Set.of(), 1)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(
            error -> error instanceof AuthenticationFailure failure && failure.status() == 403)
        .verify();
  }

  @Test
  void rejectsMissingOrInvalidSystemRoleBeforeCacheLookup() {
    for (String systemRole : Arrays.asList(null, "", "UNKNOWN")) {
      var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
      claims.put("systemRole", systemRole);
      rejects(tokens.provider().encode(claims), true);
    }
    verifyNoInteractions(cache);
  }

  @Test
  void rejectsMissingOrTenantlessRoleKeyBeforeCacheLookup() {
    for (String tenantRole : Arrays.asList(null, "", " ")) {
      var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
      claims.put("tenantRole", tenantRole);
      rejects(tokens.provider().encode(claims), true);
    }
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.remove("tenantId");
    claims.remove("tenantRoleId");
    rejects(tokens.provider().encode(claims), true);
    verifyNoInteractions(cache);
  }

  @Test
  void rejectsMissingMalformedOrTenantlessRoleIdsBeforeCacheLookup() {
    for (String roleId : Arrays.asList(null, "", "invalid-uuid")) {
      var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
      claims.put("tenantRoleId", roleId);
      rejects(tokens.provider().encode(claims), true);
    }
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.remove("tenantId");
    rejects(tokens.provider().encode(claims), true);
    verifyNoInteractions(cache);
  }

  @Test
  void rejectsRoleSnapshotFromAnotherTenant() {
    String key = AuthorizationSnapshot.role(tenant, role);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    AuthorizationSnapshot.role(UUID.randomUUID(), role),
                    "1",
                    "ACTIVE",
                    null,
                    role,
                    "OWNER",
                    Set.of("tenant.manage"),
                    0)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void rejectsRefreshExpiredForgedAndNonBearerTokens() {
    rejects(tokens.generateRefreshToken(user, tenant, Instant.now().plusSeconds(1000)), true);
    rejects(issue(new IamTokenFixture(new JwtTokenProvider(SECRET), -1000)), true);
    rejects(
        issue(
            new IamTokenFixture(
                new JwtTokenProvider("another-signing-key-with-at-least-thirty-two-bytes"))),
        true);
    rejects(issue(tokens), false);
    rejects("invalid", true);
    rejects(null, true);
  }

  @Test
  void permitsIdentityJwtWithoutTenantAndReadsOnlyUserState() {
    String jwt = tokens.generateIamAccessToken(user, null, SystemRole.USER.name(), null, Set.of());
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(identity -> assertThat(identity.tenantId()).isNull())
        .verifyComplete();
    verify(cache).get(AuthorizationSnapshot.user(user));
    verify(cache).get(AuthorizationSnapshot.session(user));
    verifyNoMoreInteractions(cache);
  }

  @Test
  void redisFailureDoesNotFallBackToJwtPermissions() {
    when(cache.get(anyString())).thenReturn(Mono.error(new IllegalStateException("Redis down")));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void reloadsLaggingUserCacheBeforeAcceptingNewLoginCredential() {
    String key = AuthorizationSnapshot.user(user);
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.put("tokenVersion", 2);
    when(cache.refresh(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(key, "2", "ACTIVE", "USER", null, null, Set.of(), 2)));
    when(cache.get(AuthorizationSnapshot.session(user)))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    AuthorizationSnapshot.session(user),
                    "1",
                    "ACTIVE",
                    null,
                    null,
                    null,
                    Set.of(),
                    2,
                    user,
                    Instant.now().plusSeconds(604800))));
    StepVerifier.create(verifier.authenticate(tokens.provider().encode(claims), true))
        .assertNext(identity -> assertThat(identity.roleId()).isEqualTo(role))
        .verifyComplete();
    verify(cache).refresh(key);
  }

  @Test
  void newerJwtCannotRaiseVersionWithoutConfirmationFromIam() {
    String key = AuthorizationSnapshot.user(user);
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.put("tokenVersion", 2);
    when(cache.refresh(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(key, "1", "ACTIVE", "USER", null, null, Set.of(), 1)));
    StepVerifier.create(verifier.authenticate(tokens.provider().encode(claims), true))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
