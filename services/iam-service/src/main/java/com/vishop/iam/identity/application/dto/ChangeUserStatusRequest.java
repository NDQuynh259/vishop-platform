package com.vishop.iam.identity.application.dto;

import jakarta.validation.constraints.*;

public record ChangeUserStatusRequest(@NotBlank String status) {}
