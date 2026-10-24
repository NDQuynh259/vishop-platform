package com.vishop.iam.modules.tenant.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record RenameTenantRequest(@NotBlank @Size(max = 200) String name) {}
