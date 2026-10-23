package com.vishop.iam.tenant.application.dto;

import jakarta.validation.constraints.*;

public record RenameTenantRequest(@NotBlank @Size(max = 200) String name) {}
