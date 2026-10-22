package com.vishop.identity.application.service;

import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import com.vishop.identity.application.command.LoginCommand;
import com.vishop.identity.application.command.RegisterUserCommand;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.LoginRequest;
import com.vishop.identity.application.dto.RegisterRequest;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.handler.GetUserProfileQueryHandler;
import com.vishop.identity.application.handler.LoginCommandHandler;
import com.vishop.identity.application.handler.RegisterUserCommandHandler;
import com.vishop.identity.application.port.PasswordHasherPort;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.application.port.UserEventPublisherPort;
import com.vishop.identity.application.query.GetUserProfileQuery;
import com.vishop.identity.domain.repository.UserRepositoryPort;
import com.vishop.identity.infrastructure.security.BCryptPasswordHasherAdapter;
import com.vishop.identity.infrastructure.security.JwtTokenIssuerAdapter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Application Service Facade coordinating identity & authentication use cases.
 * Dispatches to domain command/query handlers.
 */
@Service
public class AuthService {

    private final RegisterUserCommandHandler registerHandler;
    private final LoginCommandHandler loginHandler;
    private final GetUserProfileQueryHandler getMeHandler;

    @Autowired
    public AuthService(
            RegisterUserCommandHandler registerHandler,
            LoginCommandHandler loginHandler,
            GetUserProfileQueryHandler getMeHandler) {
        this.registerHandler = registerHandler;
        this.loginHandler = loginHandler;
        this.getMeHandler = getMeHandler;
    }

    /**
     * Backward-compatible constructor for testing and manual wiring.
     */
    public AuthService(
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        PasswordHasherPort hasher = new BCryptPasswordHasherAdapter(passwordEncoder);
        TokenIssuerPort tokenIssuer = new JwtTokenIssuerAdapter(jwtTokenProvider);
        UserEventPublisherPort eventPublisher = event -> {};
        this.registerHandler = new RegisterUserCommandHandler(userRepository, hasher, tokenIssuer, eventPublisher);
        this.loginHandler = new LoginCommandHandler(userRepository, hasher, tokenIssuer, eventPublisher);
        this.getMeHandler = new GetUserProfileQueryHandler(userRepository);
    }

    public AuthResponseDto login(LoginRequest request) {
        return loginHandler.handle(new LoginCommand(request.email(), request.password()));
    }

    public AuthResponseDto register(RegisterRequest request) {
        return registerHandler.handle(new RegisterUserCommand(
                request.email(),
                request.password(),
                request.fullName(),
                request.tenantId(),
                request.role()
        ));
    }

    public UserResponseDto getMe(UUID userId) {
        return getMeHandler.handle(new GetUserProfileQuery(userId));
    }
}
