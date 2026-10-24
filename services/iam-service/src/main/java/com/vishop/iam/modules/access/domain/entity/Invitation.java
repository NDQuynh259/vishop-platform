package com.vishop.iam.modules.access.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record Invitation(
    UUID id,
    UUID tenantId,
    String email,
    UUID roleId,
    UUID invitedBy,
    String status,
    Instant expiresAt) {}
