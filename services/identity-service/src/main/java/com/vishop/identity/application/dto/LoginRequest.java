package com.vishop.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "User login credentials payload")
public record LoginRequest(
        @Schema(description = "Registered email address", example = "admin@vishop.vn")
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Account password", example = "SecretPassword123!")
        @NotBlank(message = "Password must not be blank")
        @Size(min = 6, message = "Password must have at least 6 characters")
        String password
) {}
