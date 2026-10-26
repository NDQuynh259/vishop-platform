package com.vishop.iam.modules.identity.application.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ResolvedIdentity(
    UUID userId, UUID tenantId, String systemRole, List<String> roles, Set<String> permissions) {}
