package com.vishop.gateway.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.coreplatform.common.cache.AuthorizationCacheKeys;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.gateway.AuthorizationSnapshot;
import io.github.coreplatform.common.security.gateway.AuthorizationStatus;
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
                        AuthorizationStatus.ACTIVE,
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
                        UnauthorizedException.class, f -> assertThat(f.getCode()).isNotBlank()))
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
    verify(cache).get(AuthorizationCacheKeys.user(user));
    verifyNoMoreInteractions(cache);
  }

  @Test
  void directAccessTokenVerificationRejectsRevokedVersion() {
    String key = AuthorizationCacheKeys.user(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "2", AuthorizationStatus.ACTIVE, "USER", null, null, Set.of(), 2)));
    StepVerifier.create(verifier.verifyAccessToken(issue(tokens)))
        .expectErrorMatches(
            error ->
                error instanceof UnauthorizedException failure
                    && "token-revoked".equals(failure.getCode()))
        .verify();
    verify(cache).get(key);
    verifyNoMoreInteractions(cache);
  }

  @Test
  void usesMembershipRoleAfterVersionAndSessionChecks() {
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .assertNext(
            identity -> {
              assertThat(identity.userId()).isEqualTo(user);
              assertThat(identity.tenantId()).isEqualTo(tenant);
              assertThat(identity.roleId()).isEqualTo(role);
              assertThat(identity.actor().permissions()).isEmpty();
            })
        .verifyComplete();
    verify(cache).get(AuthorizationCacheKeys.user(user));
    verify(cache).get(AuthorizationCacheKeys.session(user));
    verify(cache).get(AuthorizationCacheKeys.tenant(tenant));
    verify(cache).get(AuthorizationCacheKeys.member(tenant, user));
    verify(cache).get(AuthorizationCacheKeys.role(tenant, role));
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
    String key = AuthorizationCacheKeys.session(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "2",
                    kind.equals("revoked")
                        ? AuthorizationStatus.REVOKED
                        : AuthorizationStatus.ACTIVE,
                    null,
                    null,
                    null,
                    Set.of(),
                    kind.equals("wrong-version") ? 2 : 1,
                    kind.equals("wrong-user") ? UUID.randomUUID() : user,
                    Instant.now().plusSeconds(kind.equals("expired") ? -1 : 3600))));
    rejects(issue(tokens), true);
    verify(cache, never()).get(AuthorizationCacheKeys.tenant(tenant));
    verify(cache, never()).get(AuthorizationCacheKeys.role(tenant, role));
  }

  @Test
  void permissionEditsApplyToSameCredentialWithoutRevocationOrRefresh() {
    String jwt = issue(tokens);
    String key = AuthorizationCacheKeys.role(tenant, role);
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(identity -> assertThat(identity.actor().permissions()).isEmpty())
        .verifyComplete();
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "2",
                    AuthorizationStatus.ACTIVE,
                    null,
                    role,
                    "OWNER",
                    Set.of("inventory.view"),
                    0)));
    StepVerifier.create(verifier.authenticate(jwt, true))
        .assertNext(identity -> assertThat(identity.actor().permissions()).isEmpty())
        .verifyComplete();
  }

  @Test
  void roleReassignmentUsesMembershipInsteadOfTheOldJwtRole() {
    UUID newRole = UUID.randomUUID();
    String memberKey = AuthorizationCacheKeys.member(tenant, user);
    when(cache.get(memberKey))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    memberKey,
                    "2",
                    AuthorizationStatus.ACTIVE,
                    null,
                    newRole,
                    null,
                    Set.of(),
                    0,
                    user,
                    null)));
    String roleKey = AuthorizationCacheKeys.role(tenant, newRole);
    when(cache.get(roleKey))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    roleKey,
                    "1",
                    AuthorizationStatus.ACTIVE,
                    null,
                    newRole,
                    "VIEWER",
                    Set.of("tenant.view"),
                    0)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .assertNext(
            identity -> {
              assertThat(identity.roleId()).isEqualTo(newRole);
              assertThat(identity.actor().tenantRole()).isEqualTo("VIEWER");
            })
        .verifyComplete();
    verify(cache, never()).get(AuthorizationCacheKeys.role(tenant, role));
  }

  @Test
  void inactiveMembershipOnlyBlocksItsOwnTenant() {
    String key = AuthorizationCacheKeys.member(tenant, user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "2",
                    AuthorizationStatus.INACTIVE,
                    null,
                    role,
                    null,
                    Set.of(),
                    0,
                    user,
                    null)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(
            error ->
                error instanceof ForbiddenException denied
                    && "membership-inactive".equals(denied.getCode()))
        .verify();
    UUID otherTenant = UUID.randomUUID();
    String otherToken = tokens.generateIamAccessToken(user, otherTenant, "USER", "OWNER", Set.of());
    StepVerifier.create(verifier.authenticate(otherToken, true))
        .assertNext(identity -> assertThat(identity.tenantId()).isEqualTo(otherTenant))
        .verifyComplete();
  }

  @Test
  void rejectsRevokedVersionBeforeUsingJwtRole() {
    String key = AuthorizationCacheKeys.user(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "2", AuthorizationStatus.ACTIVE, "USER", null, null, Set.of(), 2)));
    rejects(issue(tokens), true);
    verify(cache, never()).get(AuthorizationCacheKeys.tenant(tenant));
    verify(cache, never()).get(AuthorizationCacheKeys.role(tenant, role));
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "tenant", "member", "role"})
  void deniesInactiveUserTenantOrRole(String kind) {
    String key =
        switch (kind) {
          case "user" -> AuthorizationCacheKeys.user(user);
          case "tenant" -> AuthorizationCacheKeys.tenant(tenant);
          case "member" -> AuthorizationCacheKeys.member(tenant, user);
          default -> AuthorizationCacheKeys.role(tenant, role);
        };
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "2",
                    AuthorizationStatus.INACTIVE,
                    "USER",
                    role,
                    "OWNER",
                    Set.of(),
                    1,
                    user,
                    null)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(error -> error instanceof ForbiddenException)
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
    String key = AuthorizationCacheKeys.role(tenant, role);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    AuthorizationCacheKeys.role(UUID.randomUUID(), role),
                    "1",
                    AuthorizationStatus.ACTIVE,
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
    verify(cache).get(AuthorizationCacheKeys.user(user));
    verify(cache).get(AuthorizationCacheKeys.session(user));
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
    String key = AuthorizationCacheKeys.user(user);
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.put("tokenVersion", 2);
    when(cache.refresh(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "2", AuthorizationStatus.ACTIVE, "USER", null, null, Set.of(), 2)));
    when(cache.get(AuthorizationCacheKeys.session(user)))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    AuthorizationCacheKeys.session(user),
                    "1",
                    AuthorizationStatus.ACTIVE,
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
    String key = AuthorizationCacheKeys.user(user);
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.put("tokenVersion", 2);
    when(cache.refresh(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "1", AuthorizationStatus.ACTIVE, "USER", null, null, Set.of(), 1)));
    StepVerifier.create(verifier.authenticate(tokens.provider().encode(claims), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "session", "tenant", "member", "role"})
  void rejectsEmptyCacheSnapshotsInsteadOfCompletingWithoutAnIdentity(String kind) {
    when(cache.get(snapshotKey(kind))).thenReturn(Mono.empty());
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"user", "session", "tenant", "member", "role"})
  void rejectsSnapshotFromAnotherIdentityOrScope(String kind) {
    when(cache.get(snapshotKey(kind)))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    "wrong-key",
                    "1",
                    AuthorizationStatus.ACTIVE,
                    "USER",
                    role,
                    "OWNER",
                    Set.of(),
                    1,
                    user,
                    Instant.now().plusSeconds(3600))));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "UNKNOWN"})
  void rejectsInvalidSystemRoleInUserCache(String systemRole) {
    String key = AuthorizationCacheKeys.user(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key, "1", AuthorizationStatus.ACTIVE, systemRole, null, null, Set.of(), 1)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
    verify(cache, never()).get(AuthorizationCacheKeys.session(user));
  }

  @Test
  void inactiveUserTombstoneIsDeniedEvenWhenAccountDetailsAreMissing() {
    String key = AuthorizationCacheKeys.user(user);
    var tombstone =
        new AuthorizationSnapshot(
            key, "2", AuthorizationStatus.INACTIVE, null, null, null, Set.of(), 0);
    when(cache.get(key)).thenReturn(Mono.just(tombstone));
    when(cache.refresh(key)).thenReturn(Mono.just(tombstone));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(ForbiddenException.class)
        .verify();
    verify(cache, never()).get(AuthorizationCacheKeys.session(user));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-expiry", "expired"})
  void distinguishesMissingSessionDataFromExpiredCredentials(String kind) {
    String key = AuthorizationCacheKeys.session(user);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "1",
                    AuthorizationStatus.ACTIVE,
                    null,
                    null,
                    null,
                    Set.of(),
                    1,
                    user,
                    kind.equals("missing-expiry") ? null : Instant.now().minusSeconds(1))));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectErrorMatches(
            error ->
                kind.equals("missing-expiry")
                    ? error instanceof IllegalStateException
                    : error instanceof UnauthorizedException denied
                        && "session-expired".equals(denied.getCode()))
        .verify();
    verify(cache, never()).get(AuthorizationCacheKeys.member(tenant, user));
  }

  @Test
  void rejectsRoleWithoutAKeyBeforeBuildingThePrincipal() {
    String key = AuthorizationCacheKeys.role(tenant, role);
    when(cache.get(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    key,
                    "1",
                    AuthorizationStatus.ACTIVE,
                    null,
                    role,
                    null,
                    Set.of("tenant.view"),
                    0)));
    StepVerifier.create(verifier.authenticate(issue(tokens), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void validatesTheIdentityOfARefreshedUserSnapshot() {
    String key = AuthorizationCacheKeys.user(user);
    var claims = new HashMap<String, Object>(tokens.parseClaims(issue(tokens)));
    claims.put("tokenVersion", 2);
    when(cache.refresh(key))
        .thenReturn(
            Mono.just(
                new AuthorizationSnapshot(
                    AuthorizationCacheKeys.user(UUID.randomUUID()),
                    "2",
                    AuthorizationStatus.ACTIVE,
                    "USER",
                    null,
                    null,
                    Set.of(),
                    2)));
    StepVerifier.create(verifier.authenticate(tokens.provider().encode(claims), true))
        .expectError(IllegalStateException.class)
        .verify();
  }

  private String snapshotKey(String kind) {
    return switch (kind) {
      case "user" -> AuthorizationCacheKeys.user(user);
      case "session" -> AuthorizationCacheKeys.session(user);
      case "tenant" -> AuthorizationCacheKeys.tenant(tenant);
      case "member" -> AuthorizationCacheKeys.member(tenant, user);
      default -> AuthorizationCacheKeys.role(tenant, role);
    };
  }
}
