package com.vishop.iam.identity.application.dto;

import jakarta.validation.constraints.*;

public record IntrospectRequest(
    @NotBlank @Size(max = 8192) String credential,
    @NotNull @Pattern(regexp = "BEARER|SESSION") String type) {}
