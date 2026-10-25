package com.vishop.iam.modules.identity.application.command.register;

import com.vishop.iam.modules.identity.application.dto.AuthResponse;
import com.vishop.iam.modules.identity.application.port.PasswordHasherPort;
import com.vishop.iam.modules.identity.application.service.TokenService;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.port.UserRepositoryPort;
import com.vishop.iam.modules.identity.domain.service.PasswordPolicy;
import com.vishop.iam.shared.application.port.IamEventPublisherPort;
import io.github.coreplatform.common.domain.cqrs.CommandHandler;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegisterUserCommandHandler
    implements CommandHandler<RegisterUserCommand, AuthResponse> {
  private final UserRepositoryPort userRepository;
  private final IamEventPublisherPort outbox;
  private final PasswordHasherPort passwords;
  private final TokenService tokens;

  public RegisterUserCommandHandler(
      UserRepositoryPort userRepository,
      IamEventPublisherPort outbox,
      PasswordHasherPort passwords,
      TokenService tokens) {
    this.userRepository = userRepository;
    this.outbox = outbox;
    this.passwords = passwords;
    this.tokens = tokens;
  }

  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public AuthResponse handle(RegisterUserCommand command) {
    var email = command.email();
    var password = command.password();
    var name = command.name();
    var requestedTenant = command.requestedTenant();
    var requestedRole = command.requestedRole();

    if (requestedTenant != null || (requestedRole != null && !requestedRole.isBlank()))
      throw new ForbiddenException("Public registration cannot assign a tenant or role");
    PasswordPolicy.validate(password);
    email = email.trim().toLowerCase(Locale.ROOT);
    if (userRepository.userByEmail(email).isPresent())
      throw new ConflictException("Email is already registered");
    User user = userRepository.createUser(email, passwords.encode(password), name.trim());
    outbox.append("UserRegistered", "User", user.id(), null, Map.of("userId", user.id()));
    return tokens.login(user);
  }
}
