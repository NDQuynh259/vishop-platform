package com.vishop.iam.modules.identity.application.dto;

import com.vishop.iam.contracts.identity.UserView;
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
