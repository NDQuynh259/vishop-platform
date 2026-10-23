package com.vishop.iam.access.application.dto;

import jakarta.validation.constraints.*;

public record AcceptInvitationRequest(@NotBlank @Size(max = 256) String token) {}
