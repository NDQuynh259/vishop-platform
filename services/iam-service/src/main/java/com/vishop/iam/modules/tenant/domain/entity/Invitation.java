package com.vishop.iam.modules.tenant.domain.entity;

import com.vishop.iam.modules.tenant.domain.valueobject.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

public record Invitation(
    UUID id,
    UUID tenantId,
    String email,
    UUID roleId,
    UUID invitedBy,
    InvitationStatus status,
    Instant expiresAt) {}
