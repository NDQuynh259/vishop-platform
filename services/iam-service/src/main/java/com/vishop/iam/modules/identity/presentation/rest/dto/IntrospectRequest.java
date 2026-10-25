package com.vishop.iam.modules.identity.presentation.rest.dto;

import jakarta.validation.constraints.*;

public record IntrospectRequest(
    @NotBlank @Size(max = 8192) String credential,
    @NotNull @Pattern(regexp = "BEARER") String type) {}
