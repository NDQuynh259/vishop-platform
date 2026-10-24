package com.vishop.iam.contracts.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponseDto(
    UUID id, String code, String name, String status, Instant createdAt) {}
