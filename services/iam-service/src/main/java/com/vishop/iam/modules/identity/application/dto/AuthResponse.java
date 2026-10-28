package com.vishop.iam.modules.identity.application.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserView user,
    UUID tenantId,
    String tenantRole,
    Set<String> permissions,
    Instant refreshExpiresAt) {}
