package com.vishop.iam.access.application.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record InviteMemberRequest(
    @NotBlank @Email @Size(max = 255) String email, @NotNull UUID roleId) {}
