package io.github.coreplatform.common.security.gateway;

import java.util.Set;
import java.util.UUID;

/** Service identity built from trusted gateway headers and locally resolved role permissions. */
public record GatewayPrincipal(
    UUID userId,
    UUID tenantId,
    String systemRole,
    String tenantRole,
    Set<String> permissions,
    UUID sessionId) {
  public GatewayPrincipal(
      UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
    this(userId, tenantId, systemRole, tenantRole, permissions, null);
  }

  public GatewayPrincipal {
    permissions = Set.copyOf(permissions);
  }
}
