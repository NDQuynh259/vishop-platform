package com.vishop.iam.modules.identity.domain.entity;

import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UUID userId, UUID activeTenantId, Instant expiresAt) {}
