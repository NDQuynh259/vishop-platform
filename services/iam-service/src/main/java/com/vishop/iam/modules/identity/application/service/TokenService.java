package com.vishop.iam.modules.identity.application.service;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.shared.application.dto.IamActor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
  private final AccessApi access;
  private final IdentityAuthorizationService identities;
  private final AccessTokenPort tokens;

  public TokenService(
      AccessApi access, IdentityAuthorizationService identities, AccessTokenPort tokens) {
    this.access = access;
    this.identities = identities;
    this.tokens = tokens;
  }

  public AuthResponse login(User user) {
    var memberships = access.activeTenantIds(user.id());
    UUID selected = memberships.size() == 1 ? memberships.getFirst() : null;
    return response(user, selected, Instant.now().plusMillis(tokens.refreshExpirationMs()));
  }

  public AuthResponse refresh(String credential) {
    var refresh = tokens.verifyRefresh(credential);
    return response(
        identities.activeUser(refresh.userId()), refresh.tenantId(), refresh.expiresAt());
  }

  public AuthResponse switchTenant(IamActor actor, UUID tenant, String credential) {
    var refresh = tokens.verifyRefresh(credential);
    if (!refresh.userId().equals(actor.userId()))
      throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
          "Token owner mismatch");
    return response(identities.activeUser(actor.userId()), tenant, refresh.expiresAt());
  }

  private AuthResponse response(User user, UUID selected, Instant refreshExpiry) {
    var actor = principal(user, selected);
    return new AuthResponse(
        tokens.issue(actor),
        tokens.issueRefresh(user.id(), selected, refreshExpiry),
        "Bearer",
        tokens.expirationMs() / 1000,
        UserViewMapper.from(user),
        selected,
        actor.roles(),
        actor.permissions());
  }

  private IamActor principal(User user, UUID tenant) {
    if (tenant != null) {
      access.requireAccess(user.id(), tenant, null);
      var grant =
          access
              .activeGrant(user.id(), tenant)
              .orElseThrow(
                  () ->
                      new io.github.coreplatform.common.domain.exception.ForbiddenException(
                          "Active tenant membership is required"));
      return new IamActor(
          user.id(),
          tenant,
          user.systemRole().name(),
          List.of(grant.roleKey()),
          grant.permissions());
    }
    return new IamActor(user.id(), null, user.systemRole().name(), List.of(), Set.of());
  }
}
