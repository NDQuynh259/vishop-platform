package com.vishop.iam.modules.identity.application.service;

import com.vishop.iam.modules.identity.application.api.IdentityApi.TenantAccessPort;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.TokenPort;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
  private final TenantAccessPort access;
  private final IdentityAuthorizationService identities;
  private final TokenPort tokens;
  private final UserRepositoryPort users;

  public TokenService(
      TenantAccessPort access,
      IdentityAuthorizationService identities,
      TokenPort tokens,
      UserRepositoryPort users) {
    this.access = access;
    this.identities = identities;
    this.tokens = tokens;
    this.users = users;
  }

  public AuthResponse login(User user) {
    var memberships = access.activeTenantIds(user.id());
    // A remembered tenant is only a preference; current active membership is still required.
    UUID selected =
        user.lastTenantId() != null && memberships.contains(user.lastTenantId())
            ? user.lastTenantId()
            : memberships.size() == 1 ? memberships.getFirst() : null;
    var response = response(user, selected, Instant.now().plusMillis(tokens.refreshExpirationMs()));
    rememberTenant(user, selected);
    return response;
  }

  public AuthResponse refresh(String credential) {
    var refresh = tokens.verifyRefresh(credential);
    return response(
        identities.activeUser(refresh.userId()), refresh.tenantId(), refresh.expiresAt());
  }

  public AuthResponse switchTenant(AuthenticatedUser actor, UUID tenant, String credential) {
    var refresh = tokens.verifyRefresh(credential);
    if (!refresh.userId().equals(actor.userId()))
      throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
          "Token owner mismatch");
    var user = identities.activeUser(actor.userId());
    // Build the response first so rejected access never changes the remembered tenant.
    var response = response(user, tenant, refresh.expiresAt());
    rememberTenant(user, tenant);
    return response;
  }

  private void rememberTenant(User user, UUID selected) {
    if (!java.util.Objects.equals(user.lastTenantId(), selected))
      users.updateLastTenant(user.id(), selected);
  }

  private AuthResponse response(User user, UUID selected, Instant refreshExpiry) {
    var identity = principal(user, selected);
    var actor = identity.actor();
    return new AuthResponse(
        tokens.issue(actor, identity.roleId()),
        tokens.issueRefresh(user.id(), selected, refreshExpiry),
        UserViewMapper.from(user),
        selected,
        actor.tenantRole(),
        actor.permissions(),
        refreshExpiry.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
  }

  private record TokenIdentity(AuthenticatedUser actor, UUID roleId) {}

  private TokenIdentity principal(User user, UUID tenant) {
    if (tenant != null) {
      var grant =
          access
              .activeGrant(user.id(), tenant)
              .orElseThrow(
                  () ->
                      new io.github.coreplatform.common.domain.exception.ForbiddenException(
                          "Active tenant membership is required"));
      return new TokenIdentity(
          new AuthenticatedUser(
              user.id(), tenant, user.systemRole().name(), grant.roleKey(), grant.permissions()),
          grant.roleId());
    }
    return new TokenIdentity(
        new AuthenticatedUser(user.id(), null, user.systemRole().name(), null, Set.of()), null);
  }
}
