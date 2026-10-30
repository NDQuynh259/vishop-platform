package com.vishop.iam.modules.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import io.github.coreplatform.common.domain.constants.SystemRole;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class JwtTokenAdapterTest {
  private final JwtTokenProvider jwt =
      new JwtTokenProvider("test-access-db-secret-with-at-least-thirty-two-bytes");
  private final IamJwtTokenFactory factory = new IamJwtTokenFactory(jwt, 900000);
  private final EntityManager db = mock(EntityManager.class);
  private final TenantAccessPort access = mock(TenantAccessPort.class);
  private final JwtTokenAdapter adapter = new JwtTokenAdapter(jwt, factory, db, access, 604800000);

  private Query userCheck(long count) {
    Query query = mock(Query.class);
    when(db.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getSingleResult()).thenReturn(count);
    return query;
  }

  @Test
  void accessVerificationChecksCurrentUserAndRoleBeforeReturningClaims() {
    UUID user = UUID.randomUUID(), tenant = UUID.randomUUID(), role = UUID.randomUUID();
    var actor = new AuthenticatedUser(user, tenant, "USER", "WAREHOUSE", Set.of("inventory.view"));
    var query = userCheck(1);
    when(access.hasActiveRole(user, tenant, role, "WAREHOUSE")).thenReturn(true);
    var claims = adapter.verifyAccessToken(factory.access(actor, role, 17));
    assertThat(claims.userId()).isEqualTo(user);
    assertThat(claims.tenantId()).isEqualTo(tenant);
    assertThat(claims.systemRole()).isEqualTo(SystemRole.USER);
    assertThat(claims.tenantRoleId()).isEqualTo(role);
    assertThat(claims.tokenVersion()).isEqualTo(17);
    verify(query).setParameter("id", user);
    verify(query).setParameter("version", 17L);
    verify(query).setParameter("systemRole", "USER");
    verify(access).hasActiveRole(user, tenant, role, "WAREHOUSE");
    verifyNoMoreInteractions(access);
  }

  @Test
  void revokedInactiveOrUnknownUserIsRejectedBeforeTenantLookup() {
    userCheck(0);
    var actor =
        new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "USER", "OWNER", Set.of());
    assertThatThrownBy(() -> adapter.verifyAccessToken(factory.access(actor, UUID.randomUUID(), 1)))
        .isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(access);
  }

  @Test
  void selectedTenantMustStillHaveTheClaimedActiveRole() {
    userCheck(1);
    var actor =
        new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "USER", "OWNER", Set.of());
    assertThatThrownBy(() -> adapter.verifyAccessToken(factory.access(actor, UUID.randomUUID(), 1)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void globalIdentityStillChecksUserButNeedsNoMembership() {
    userCheck(1);
    var actor = new AuthenticatedUser(UUID.randomUUID(), null, "USER", null, Set.of());
    assertThat(adapter.verifyAccessToken(factory.access(actor, null, 1)).tenantId()).isNull();
    verifyNoInteractions(access);
  }

  @Test
  void invalidAccessIsRejectedBeforePersistenceReads() {
    assertThatThrownBy(() -> adapter.verifyAccessToken("invalid-token"))
        .isInstanceOf(UnauthorizedException.class);
    String refresh = factory.refresh(UUID.randomUUID(), null, Instant.now().plusSeconds(600), 1);
    assertThatThrownBy(() -> adapter.verifyAccessToken(refresh))
        .isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(db, access);
  }

  @Test
  void databaseFailureIsNotMisreportedAsAnInvalidCredential() {
    when(db.createNativeQuery(anyString()))
        .thenThrow(new PersistenceException("Database unavailable"));
    var actor = new AuthenticatedUser(UUID.randomUUID(), null, "USER", null, Set.of());
    assertThatThrownBy(() -> adapter.verifyAccessToken(factory.access(actor, null, 1)))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  void refreshStillRejectsRevokedVersionAndReturnsCurrentClaims() {
    UUID user = UUID.randomUUID();
    Query query = mock(Query.class);
    when(db.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter("id", user)).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of(2L));
    Instant expires =
        Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    String revoked = factory.refresh(user, null, expires, 1);
    assertThatThrownBy(() -> adapter.verifyRefreshToken(revoked))
        .isInstanceOf(UnauthorizedException.class);
    var current = adapter.verifyRefreshToken(factory.refresh(user, null, expires, 2));
    assertThat(current.userId()).isEqualTo(user);
    assertThat(current.tokenVersion()).isEqualTo(2);
    assertThat(current.expiresAt()).isEqualTo(expires);
    verify(query, times(2)).getResultList();
  }
}
