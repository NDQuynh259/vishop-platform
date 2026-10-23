package com.vishop.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coreplatform.common.domain.exception.DomainException;
import com.vishop.identity.application.command.RegisterUserCommand;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.port.PasswordHasherPort;
import com.vishop.identity.application.port.TokenIssuerPort;
import com.vishop.identity.application.port.UserEventPublisherPort;
import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.exception.EmailAlreadyExistsException;
import com.vishop.identity.domain.ports.UserRepositoryPort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterUserCommandHandlerTest {

    private UserRepositoryPort userRepository;
    private PasswordHasherPort passwordHasher;
    private TokenIssuerPort tokenIssuer;
    private UserEventPublisherPort eventPublisher;
    private RegisterUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        passwordHasher = mock(PasswordHasherPort.class);
        tokenIssuer = mock(TokenIssuerPort.class);
        eventPublisher = mock(UserEventPublisherPort.class);
        handler = new RegisterUserCommandHandler(userRepository, passwordHasher, tokenIssuer, eventPublisher);
    }

    @Test
    @DisplayName("Should successfully handle RegisterUserCommand")
    void shouldHandleRegistration() {
        UUID tenantId = UUID.randomUUID();
        RegisterUserCommand command = new RegisterUserCommand(
                "user@vishop.vn",
                "StrongPass123!",
                "Valid User",
                tenantId,
                "ADMIN"
        );

        when(userRepository.existsByEmail("user@vishop.vn")).thenReturn(false);
        when(passwordHasher.encode("StrongPass123!")).thenReturn("$2a$10$encodedHash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenIssuer.generateAccessToken(any(User.class), anyList(), anyList())).thenReturn("mock.jwt.token");
        when(tokenIssuer.getExpirationSeconds()).thenReturn(86400L);

        AuthResponseDto result = handler.handle(command);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(result.user().email()).isEqualTo("user@vishop.vn");
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    @DisplayName("Should reject registration when password is weak")
    void shouldRejectWeakPassword() {
        RegisterUserCommand command = new RegisterUserCommand(
                "user@vishop.vn",
                "weak",
                "Valid User",
                UUID.randomUUID(),
                "STAFF"
        );

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Password must be at least 8 characters long");
    }

    @Test
    @DisplayName("Should reject registration when email already exists")
    void shouldRejectDuplicateEmail() {
        RegisterUserCommand command = new RegisterUserCommand(
                "existing@vishop.vn",
                "StrongPass123!",
                "Valid User",
                UUID.randomUUID(),
                "STAFF"
        );

        when(userRepository.existsByEmail("existing@vishop.vn")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email address is already registered");
    }
}
