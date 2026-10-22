package com.vishop.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vishop.identity.application.command.LoginCommand;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.port.PasswordHasherPort;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.application.port.UserEventPublisherPort;
import com.vishop.identity.domain.aggregate.User;
import com.vishop.identity.domain.exception.InvalidCredentialsException;
import com.vishop.identity.domain.exception.UserSuspendedException;
import com.vishop.identity.domain.repository.UserRepositoryPort;
import com.vishop.identity.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginCommandHandlerTest {

    private UserRepositoryPort userRepository;
    private PasswordHasherPort passwordHasher;
    private TokenIssuerPort tokenIssuer;
    private UserEventPublisherPort eventPublisher;
    private LoginCommandHandler handler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        passwordHasher = mock(PasswordHasherPort.class);
        tokenIssuer = mock(TokenIssuerPort.class);
        eventPublisher = mock(UserEventPublisherPort.class);
        handler = new LoginCommandHandler(userRepository, passwordHasher, tokenIssuer, eventPublisher);
    }

    @Test
    @DisplayName("Should successfully handle LoginCommand with valid credentials")
    void shouldHandleLoginSuccessfully() {
        User user = User.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin@vishop.vn",
                "$2a$10$hashedPassword",
                "Admin",
                "OWNER",
                UserStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                0L
        );

        when(userRepository.findByEmail("admin@vishop.vn")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("ValidPassword123!", "$2a$10$hashedPassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenIssuer.generateAccessToken(any(User.class), anyList(), anyList())).thenReturn("mock.jwt.token");
        when(tokenIssuer.getExpirationSeconds()).thenReturn(86400L);

        AuthResponseDto result = handler.handle(new LoginCommand("admin@vishop.vn", "ValidPassword123!"));

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("mock.jwt.token");
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password is incorrect")
    void shouldRejectBadPassword() {
        User user = User.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin@vishop.vn",
                "$2a$10$hashedPassword",
                "Admin",
                "OWNER",
                UserStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                0L
        );

        when(userRepository.findByEmail("admin@vishop.vn")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("WrongPassword", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("admin@vishop.vn", "WrongPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Should throw UserSuspendedException when account is suspended")
    void shouldRejectSuspendedAccount() {
        User user = User.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "suspended@vishop.vn",
                "$2a$10$hashedPassword",
                "Suspended User",
                "STAFF",
                UserStatus.SUSPENDED,
                Instant.now(),
                Instant.now(),
                0L
        );

        when(userRepository.findByEmail("suspended@vishop.vn")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("ValidPassword123!", "$2a$10$hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("suspended@vishop.vn", "ValidPassword123!")))
                .isInstanceOf(UserSuspendedException.class)
                .hasMessageContaining("suspended");
    }
}
