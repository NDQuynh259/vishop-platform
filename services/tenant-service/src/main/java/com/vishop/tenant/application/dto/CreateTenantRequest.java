package com.vishop.tenant.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a new tenant")
public record CreateTenantRequest(
        @Schema(description = "Unique tenant code/slug identifier", example = "my-store-vn")
        @NotBlank(message = "code is required")
        @Size(min = 3, max = 50, message = "code must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$", message = "code must consist of lowercase letters, digits, and hyphens")
        String code,

        @Schema(description = "Display name of the tenant", example = "My Store Vietnam")
        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name cannot exceed 200 characters")
        String name) {
}
