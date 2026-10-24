package com.vishop.iam.composition.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record CreateTenantRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$") String code,
    @NotBlank @Size(max = 200) String name) {}
