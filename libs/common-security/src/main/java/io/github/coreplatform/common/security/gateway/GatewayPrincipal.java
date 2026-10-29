package io.github.coreplatform.common.security.gateway;

import java.util.Set;
import java.util.UUID;

/** Principal propagated by the gateway context protocol, independent of user JWT claims. */
public record GatewayPrincipal(
    UUID userId, UUID tenantId, String systemRole, String tenantRole, Set<String> permissions) {
  public GatewayPrincipal {
    permissions = Set.copyOf(permissions);
  }
}
