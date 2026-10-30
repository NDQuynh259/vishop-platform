package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.application.port.PasswordHasherPort;
import com.vishop.iam.modules.identity.application.service.SessionAuthenticationService;
import com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegisterCommandHandler implements CommandHandler<RegisterCommand, AuthResult> {
  private final UserRepositoryPort users;
  private final PasswordHasherPort passwords;
  private final AuthSessionRepositoryPort sessions;
  private final AuthTokens tokens;
  private final SessionAuthenticationService authentication;
  private final IamEventPublisherPort events;

  public RegisterCommandHandler(
      UserRepositoryPort users,
      PasswordHasherPort passwords,
      AuthSessionRepositoryPort sessions,
      AuthTokens tokens,
      SessionAuthenticationService authentication,
      IamEventPublisherPort events) {
    this.users = users;
    this.passwords = passwords;
    this.sessions = sessions;
    this.tokens = tokens;
    this.authentication = authentication;
    this.events = events;
  }

  @Transactional
  public AuthResult handle(RegisterCommand command) {
    // Chỉ nhận email/password; role USER và trạng thái ACTIVE do server quyết định.
    var user =
        users.create(
            command.email().trim().toLowerCase(Locale.ROOT), passwords.encode(command.password()));
    var session =
        sessions.create(
            user.id(), user.tokenVersion(), Instant.now().plus(tokens.refreshLifetime()));
    events.append(
        "UserRegistered",
        "User",
        user.id(),
        null,
        Map.of("userId", user.id(), "sessionId", session.id()));
    return authentication.issue(user, session, null, null);
  }
}
