package com.vishop.iam.modules.identity.application.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserView user,
    UUID tenantId,
    List<String> roles,
    Set<String> permissions) {}
