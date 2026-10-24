package com.vishop.iam.modules.identity.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record ChangeUserStatusRequest(@NotBlank String status) {}
