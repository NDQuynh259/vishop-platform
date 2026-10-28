package com.vishop.iam.modules.identity.application.dto;

import java.util.Set;
import java.util.UUID;

public record ResolvedIdentity(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {}
