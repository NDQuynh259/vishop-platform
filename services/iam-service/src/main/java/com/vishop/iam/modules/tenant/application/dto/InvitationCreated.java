package com.vishop.iam.modules.tenant.application.dto;

import java.time.Instant;
import java.util.UUID;

public record InvitationCreated(UUID id, String token, Instant expiresAt) {}
