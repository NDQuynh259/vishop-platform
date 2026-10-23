package com.vishop.iam.identity.application.query;

import com.vishop.iam.identity.application.port.AccessTokenPort;
import com.vishop.iam.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.identity.application.service.SessionService;
import com.vishop.iam.identity.domain.entities.Session;
import com.vishop.iam.identity.domain.ports.SessionRepositoryPort;
import com.vishop.iam.shared.application.dto.IamActor;
import com.vishop.iam.shared.application.port.OpaqueTokenPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthenticateQueryHandler implements QueryHandler<AuthenticateQuery, IamActor> {
  private final SessionRepositoryPort sessionRepository;
  private final AccessTokenPort tokens;
  private final OpaqueTokenPort sessionTokens;
  private final IdentityAuthorizationService authorization;
  private final SessionService sessions;

  public AuthenticateQueryHandler(
      SessionRepositoryPort sessionRepository,
      AccessTokenPort tokens,
      OpaqueTokenPort sessionTokens,
      IdentityAuthorizationService authorization,
      SessionService sessions) {
    this.sessionRepository = sessionRepository;
    this.tokens = tokens;
    this.sessionTokens = sessionTokens;
    this.authorization = authorization;
    this.sessions = sessions;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public IamActor handle(AuthenticateQuery query) {
    var credential = query.credential();
    var bearer = query.bearer();

    Session session;
    UUID selected;
    if (bearer) {
      var identity = tokens.verify(credential);
      UUID user = identity.userId();
      UUID sid = identity.sessionId();
      selected = identity.tenantId();
      session =
          sessionRepository
              .session(sid)
              .orElseThrow(() -> new UnauthorizedException("Session expired or revoked"));
      if (!session.userId().equals(user))
        throw new UnauthorizedException("Session does not belong to user");
    } else {
      if (credential == null || credential.length() > 256)
        throw new UnauthorizedException("Invalid session");
      session =
          sessionRepository
              .sessionByHash(sessionTokens.hash(credential))
              .orElseThrow(() -> new UnauthorizedException("Session expired or revoked"));
      selected = session.activeTenantId();
    }
    return sessions.principal(authorization.activeUser(session.userId()), session.id(), selected);
  }
}
