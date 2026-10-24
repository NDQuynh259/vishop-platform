package com.vishop.iam.modules.identity.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record RegisterUserRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 255) String fullName,
    UUID tenantId,
    String role) {}
