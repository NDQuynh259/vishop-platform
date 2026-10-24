package com.vishop.iam.modules.identity.application.command;

import com.vishop.iam.contracts.access.AccessApi;
import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.application.service.SessionService;
import com.vishop.iam.modules.identity.domain.port.SessionRepositoryPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SwitchTenantCommandHandler
    implements CommandHandler<SwitchTenantCommand, AuthResponse> {
  private final AccessApi access;
  private final SessionRepositoryPort sessionRepository;
  private final IdentityAuthorizationService authorization;
  private final SessionService sessions;

  public SwitchTenantCommandHandler(
      AccessApi access,
      SessionRepositoryPort sessionRepository,
      IdentityAuthorizationService authorization,
      SessionService sessions) {
    this.access = access;
    this.sessionRepository = sessionRepository;
    this.authorization = authorization;
    this.sessions = sessions;
  }

  @Override
  @Transactional
  public AuthResponse handle(SwitchTenantCommand command) {
    var actor = command.actor();
    var tenant = command.tenant();

    access.requireAccess(actor.userId(), tenant, null);
    sessionRepository.selectTenant(actor.sessionId(), actor.userId(), tenant);
    var session =
        sessionRepository
            .session(actor.sessionId())
            .orElseThrow(() -> new UnauthorizedException("Session expired or revoked"));
    return sessions.response(
        sessions.principal(authorization.activeUser(actor.userId()), session.id(), tenant),
        session);
  }
}
