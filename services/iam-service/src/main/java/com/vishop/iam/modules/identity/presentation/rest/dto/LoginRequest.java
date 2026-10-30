package com.vishop.iam.modules.identity.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(max = 128) String password,
    java.util.UUID tenantId) {}
