package com.vishop.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "User profile response representation")
public record UserResponseDto(
        @Schema(description = "Unique user identifier", example = "0192a6df-10a4-7f12-a1b2-c3d4e5f60789")
        UUID id,

        @Schema(description = "Belonging organization tenant ID", example = "0192a6df-10a4-7f12-a1b2-c3d4e5f60789")
        UUID tenantId,

        @Schema(description = "Email address", example = "admin@vishop.vn")
        String email,

        @Schema(description = "Full name of the user", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "Assigned system role", example = "OWNER")
        String role,

        @Schema(description = "Account status", example = "ACTIVE")
        String status,

        @Schema(description = "Timestamp when the user account was created")
        Instant createdAt
) {}
