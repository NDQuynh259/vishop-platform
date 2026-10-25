package com.vishop.iam.modules.identity.application.service;

import com.vishop.iam.modules.access.application.api.AccessApi;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.dto.AuthenticationResult;
import com.vishop.iam.modules.identity.application.mapper.UserViewMapper;
import com.vishop.iam.modules.identity.application.port.AccessTokenPort;
import com.vishop.iam.modules.identity.domain.entity.Session;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.SessionRepositoryPort;
import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionService {
  private final SessionRepositoryPort sessions;
  private final AccessApi access;
  private final IdentityAuthorizationService identities;
  private final AccessTokenPort tokens;
  private final OpaqueTokenPort opaqueTokens;
  private final long sessionSeconds;

  public SessionService(
      SessionRepositoryPort sessions,
      AccessApi access,
      IdentityAuthorizationService identities,
      AccessTokenPort tokens,
      OpaqueTokenPort opaqueTokens,
      @Value("${vishop.security.session-seconds:604800}") long sessionSeconds) {
    if (sessionSeconds < 1) throw new IllegalArgumentException("Session duration must be positive");
    this.sessions = sessions;
    this.access = access;
    this.identities = identities;
    this.tokens = tokens;
    this.opaqueTokens = opaqueTokens;
    this.sessionSeconds = sessionSeconds;
  }

  public AuthenticationResult newSession(User user) {
    String raw = opaqueTokens.generate();
    Session session = sessions.createSession(user.id(), opaqueTokens.hash(raw), sessionSeconds);
    var memberships = access.activeTenantIds(user.id());
    UUID selected = memberships.size() == 1 ? memberships.getFirst() : null;
    if (selected != null) sessions.selectTenant(session.id(), user.id(), selected);
    return new AuthenticationResult(
        response(principal(user, session.id(), selected), session), raw);
  }

  public IamActor principal(User user, UUID session, UUID selected) {
    if (selected != null) {
      var grant = access.activeGrant(user.id(), selected);
      if (grant.isPresent())
        return new IamActor(
            user.id(),
            session,
            selected,
            user.systemRole().name(),
            List.of(grant.get().roleKey()),
            grant.get().permissions());
    }
    return new IamActor(user.id(), session, null, user.systemRole().name(), List.of(), Set.of());
  }

  public AuthResponse response(IamActor actor, Session session) {
    String token = tokens.issue(actor, session.expiresAt());
    long expires =
        Math.min(
            tokens.expirationMs() / 1000,
            Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toSeconds()));
    return new AuthResponse(
        token,
        "Bearer",
        expires,
        UserViewMapper.from(identities.activeUser(actor.userId())),
        actor.tenantId(),
        actor.roles(),
        actor.permissions());
  }
}
