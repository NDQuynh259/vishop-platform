package io.github.coreplatform.common.security.gateway;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Resolves tenant-role name and permissions from the role ID supplied by the gateway. */
@FunctionalInterface
public interface GatewayRolePermissions {
  @Getter
  @RequiredArgsConstructor
  final class Grant {
    private final String roleKey;
    private final Set<String> permissions;
  }

  Grant resolve(UUID tenantId, UUID roleId);
}
