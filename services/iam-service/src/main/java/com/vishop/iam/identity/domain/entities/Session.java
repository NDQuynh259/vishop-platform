package com.vishop.iam.identity.domain.entities;

import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UUID userId, UUID activeTenantId, Instant expiresAt) {}
