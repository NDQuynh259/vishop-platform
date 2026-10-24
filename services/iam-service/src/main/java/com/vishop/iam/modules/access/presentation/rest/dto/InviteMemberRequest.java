package com.vishop.iam.modules.access.presentation.rest.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record InviteMemberRequest(
    @NotBlank @Email @Size(max = 255) String email, @NotNull UUID roleId) {}
