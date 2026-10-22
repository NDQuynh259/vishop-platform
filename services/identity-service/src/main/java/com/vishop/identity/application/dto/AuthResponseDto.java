package com.vishop.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing access token and user profile")
public record AuthResponseDto(
        @Schema(description = "JWT Bearer access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Token type scheme", example = "Bearer")
        String tokenType,

        @Schema(description = "Token expiration duration in seconds", example = "86400")
        long expiresIn,

        @Schema(description = "Authenticated user profile")
        UserResponseDto user
) {}
