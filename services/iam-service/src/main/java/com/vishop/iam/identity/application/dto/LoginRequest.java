package com.vishop.iam.identity.application.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {}
