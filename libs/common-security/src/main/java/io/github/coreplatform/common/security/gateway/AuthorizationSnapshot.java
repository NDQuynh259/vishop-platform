package io.github.coreplatform.common.security.gateway;

import java.util.Set;
import java.util.UUID;

/** Versioned IAM projection; a role key always includes its owning tenant. */
public record AuthorizationSnapshot(
    String key,
    String version,
    String status,
    String systemRole,
    UUID roleId,
    String roleKey,
    Set<String> permissions,
    long tokenVersion) {
  public AuthorizationSnapshot {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public static String user(UUID id) {
    return "auth:user:" + id;
  }

  public static String tenant(UUID id) {
    return "auth:tenant:" + id;
  }

  public static String member(UUID tenant, UUID user) {
    return "auth:member:" + tenant + ":" + user;
  }

  public static String role(UUID tenant, UUID role) {
    return "auth:role:" + tenant + ":" + role;
  }
}
