package com.vishop.identity.presentation.rest;

import io.github.coreplatform.common.security.constants.SecurityHeaders;
import io.github.coreplatform.common.security.context.CommonUserDetails;
import com.vishop.identity.application.dto.AuthResponseDto;
import com.vishop.identity.application.dto.LoginRequest;
import com.vishop.identity.application.dto.RegisterRequest;
import com.vishop.identity.application.dto.UserResponseDto;
import com.vishop.identity.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "User authentication, registration, session cookies, and profile APIs")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String SESSION_COOKIE_NAME = "better-auth.session_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // #region login
    @Operation(
            summary = "Authenticate user",
            description = "Validates credentials, issues JWT access token in body, and sets HTTP-only session cookie for hybrid compatibility."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or account inactive")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponseDto authResponse = authService.login(request);
        setSessionCookie(response, authResponse.accessToken(), Duration.ofSeconds(authResponse.expiresIn()));

        return ResponseEntity.ok(authResponse);
    }
    // #endregion

    // #region register
    @Operation(
            summary = "Register new user",
            description = "Creates a new user aggregate, hashes password with BCrypt, issues token, and sets session cookie."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthResponseDto authResponse = authService.register(request);
        setSessionCookie(response, authResponse.accessToken(), Duration.ofSeconds(authResponse.expiresIn()));

        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }
    // #endregion

    // #region getMe
    @Operation(
            summary = "Get current authenticated user profile",
            description = "Retrieves user details for the active authenticated caller.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or missing identity context")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMe(HttpServletRequest request) {
        UUID userId = resolveCurrentUserId(request);
        UserResponseDto user = authService.getMe(userId);
        return ResponseEntity.ok(user);
    }
    // #endregion

    // #region logout
    @Operation(
            summary = "Log out user",
            description = "Clears the HTTP-only session cookie."
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok().build();
    }
    // #endregion

    // #region setSessionCookie
    private void setSessionCookie(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false) // Set to true in HTTPS production
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    // #endregion

    // #region resolveCurrentUserId
    private UUID resolveCurrentUserId(HttpServletRequest request) {
        // A. From Spring Security Context (populated by InternalHeaderAuthFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CommonUserDetails userDetails) {
            return userDetails.userId();
        }

        // B. From Header directly (if InternalHeaderAuthFilter passed it)
        String headerUserId = request.getHeader(SecurityHeaders.X_USER_ID);
        if (headerUserId != null && !headerUserId.isBlank()) {
            return UUID.fromString(headerUserId);
        }

        throw new io.github.coreplatform.common.domain.exception.UnauthorizedException(
                "Unable to identify authenticated user from security context.");
    }
    // #endregion
}
