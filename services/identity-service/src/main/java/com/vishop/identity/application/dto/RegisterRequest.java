package com.vishop.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "User registration payload")
public record RegisterRequest(
        @Schema(description = "Email address for new user", example = "owner@vishop.vn")
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Password (minimum 6 characters)", example = "SecurePass123!")
        @NotBlank(message = "Password must not be blank")
        @Size(min = 6, message = "Password must have at least 6 characters")
        String password,

        @Schema(description = "User's full name", example = "Nguyen Van A")
        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @Schema(description = "Target tenant ID (optional, generated if omitted)", example = "0192a6df-10a4-7f12-a1b2-c3d4e5f60789")
        UUID tenantId,

        @Schema(description = "Assigned system role (defaults to OWNER if not provided)", example = "OWNER")
        String role
) {}
