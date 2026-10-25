package com.vishop.iam.modules.tenant.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponseDto(
    UUID id, String code, String name, String status, Instant createdAt) {}
