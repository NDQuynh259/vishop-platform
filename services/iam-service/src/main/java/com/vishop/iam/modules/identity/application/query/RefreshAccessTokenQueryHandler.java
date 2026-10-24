package com.vishop.iam.modules.identity.application.query;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.application.service.SessionService;
import com.vishop.iam.modules.identity.domain.port.SessionRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.QueryHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshAccessTokenQueryHandler
    implements QueryHandler<RefreshAccessTokenQuery, AuthResponse> {
  private final SessionRepositoryPort sessionRepository;
  private final IdentityAuthorizationService authorization;
  private final SessionService sessions;

  public RefreshAccessTokenQueryHandler(
      SessionRepositoryPort sessionRepository,
      IdentityAuthorizationService authorization,
      SessionService sessions) {
    this.sessionRepository = sessionRepository;
    this.authorization = authorization;
    this.sessions = sessions;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AuthResponse handle(RefreshAccessTokenQuery query) {
    var actor = query.actor();

    var session =
        sessionRepository
            .session(actor.sessionId())
            .orElseThrow(() -> new UnauthorizedException("Session expired or revoked"));
    return sessions.response(
        sessions.principal(
            authorization.activeUser(actor.userId()), session.id(), actor.tenantId()),
        session);
  }
}
