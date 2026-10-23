package com.vishop.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import io.github.coreplatform.common.domain.exception.UnauthorizedException;
import io.github.coreplatform.common.security.jwt.JwtTokenProvider;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.LoginRequest;
import com.vishop.identity.application.dto.RegisterRequest;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.domain.entities.User;
import com.vishop.identity.domain.ports.UserRepositoryPort;
import com.vishop.identity.domain.valueobjects.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepositoryPort userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final String rawPassword = "Password123!";
    private final String encodedPassword = "$2a$10$encodedHashDummyValue";
    private final String email = "admin@vishop.vn";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = new JwtTokenProvider(
                "vishop-platform-super-secret-key-that-is-at-least-256-bits-long",
                3600000L);
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                email, rawPassword, "Admin User", tenantId, "OWNER");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponseDto response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo(email);
        assertThat(response.user().fullName()).isEqualTo("Admin User");
        assertThat(response.user().role()).isEqualTo("OWNER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when registering with duplicate email")
    void shouldThrowConflictOnDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                email, rawPassword, "Admin User", tenantId, "OWNER");

        when(userRepository.existsByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email address is already registered");
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        User existingUser = User.reconstitute(
                userId, tenantId, email, encodedPassword, "Admin User",
                "OWNER", UserStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        AuthResponseDto response = authService.login(new LoginRequest(email, rawPassword));

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(response.accessToken())).isTrue();
        assertThat(response.user().id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when password does not match")
    void shouldThrowUnauthorizedOnBadPassword() {
        User existingUser = User.reconstitute(
                userId, tenantId, email, encodedPassword, "Admin User",
                "OWNER", UserStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", encodedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when email does not exist")
    void shouldThrowUnauthorizedWhenEmailNotFound() {
        when(userRepository.findByEmail("unknown@vishop.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@vishop.vn", rawPassword)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Should get user profile by ID")
    void shouldGetMeSuccessfully() {
        User user = User.reconstitute(
                userId, tenantId, email, encodedPassword, "Admin User",
                "OWNER", UserStatus.ACTIVE, Instant.now(), Instant.now(), 0L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponseDto dto = authService.getMe(userId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(userId);
        assertThat(dto.email()).isEqualTo(email);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when user ID is not found")
    void shouldThrowNotFoundOnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(unknownId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
