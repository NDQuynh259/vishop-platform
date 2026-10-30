package com.vishop.iam.modules.identity.application.command.auth;

import com.vishop.iam.modules.identity.application.dto.AuthResult;
import com.vishop.iam.modules.identity.application.port.AuthTokens;
import com.vishop.iam.modules.identity.application.port.PasswordHasherPort;
import com.vishop.iam.modules.identity.application.service.SessionAuthenticationService;
import com.vishop.iam.modules.identity.domain.port.AuthSessionRepositoryPort;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginCommandHandler implements CommandHandler<LoginCommand, AuthResult> {
  private final UserRepositoryPort users;
  private final PasswordHasherPort passwords;
  private final AuthSessionRepositoryPort sessions;
  private final AuthTokens tokens;
  private final SessionAuthenticationService authentication;
  private final IamEventPublisherPort events;

  public LoginCommandHandler(
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
  public AuthResult handle(LoginCommand command) {
    // Khóa user trong lúc cấp token: không cấp dựa trên password/status/version đang bị cập nhật.
    var found = users.lockByEmail(command.email().trim().toLowerCase(Locale.ROOT));
    if (!passwords.matches(command.password(), found.map(u -> u.passwordHash()).orElse(null)))
      throw new UnauthorizedException("Invalid email or password");
    var user = found.orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    user.requireActive();
    var tenant = authentication.selectAtLogin(user, command.tenantId());
    if (tenant != null) users.rememberTenant(user.id(), tenant);
    var session =
        sessions.create(
            user.id(), user.tokenVersion(), Instant.now().plus(tokens.refreshLifetime()));
    // Mỗi login sinh sid mới, vì vậy logout trình duyệt A không làm mất phiên trên B.
    events.append(
        "UserLoggedIn",
        "User",
        user.id(),
        tenant,
        Map.of("userId", user.id(), "sessionId", session.id()));
    return authentication.issue(user, session, tenant, null);
  }
}
