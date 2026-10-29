package com.vishop.iam.modules.tenant.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public record RoleRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Size(max = 100) Set<@NotBlank @Size(max = 100) String> permissions) {}
