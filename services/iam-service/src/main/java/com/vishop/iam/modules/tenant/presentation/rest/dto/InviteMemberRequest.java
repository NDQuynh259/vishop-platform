package com.vishop.iam.modules.tenant.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record InviteMemberRequest(
    @NotBlank @Email @Size(max = 255) String email, @NotNull UUID roleId) {}
