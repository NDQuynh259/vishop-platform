package com.vishop.iam.modules.identity.application.command;

import com.vishop.iam.modules.identity.application.dto.AuthenticationResult;
import com.vishop.iam.modules.identity.application.port.PasswordHasherPort;
import com.vishop.iam.modules.identity.application.service.IdentityAuthorizationService;
import com.vishop.iam.modules.identity.application.service.SessionService;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginCommandHandler implements CommandHandler<LoginCommand, AuthenticationResult> {
  private final UserRepositoryPort userRepository;
  private final IamEventPublisherPort outbox;
  private final PasswordHasherPort passwords;
  private final IdentityAuthorizationService authorization;
  private final SessionService sessions;

  public LoginCommandHandler(
      UserRepositoryPort userRepository,
      IamEventPublisherPort outbox,
      PasswordHasherPort passwords,
      IdentityAuthorizationService authorization,
      SessionService sessions) {
    this.userRepository = userRepository;
    this.outbox = outbox;
    this.passwords = passwords;
    this.authorization = authorization;
    this.sessions = sessions;
  }

  @Override
  @Transactional
  public AuthenticationResult handle(LoginCommand command) {
    var email = command.email();
    var password = command.password();

    User user =
        userRepository
            .userByEmail(email.trim())
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    if (!passwords.matches(password, user.passwordHash()))
      throw new UnauthorizedException("Invalid email or password");
    authorization.activeUser(user.id());
    outbox.append("UserLoggedIn", "User", user.id(), null, Map.of("userId", user.id()));
    return sessions.newSession(user);
  }
}
