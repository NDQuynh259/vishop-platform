package com.vishop.iam.modules.tenant.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record AcceptInvitationRequest(@NotBlank @Size(max = 256) String token) {}
