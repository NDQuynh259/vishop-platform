package io.github.coreplatform.common.cache;

import java.util.Objects;
import java.util.UUID;

/** Key logic phân quyền dùng chung cho IAM và gateway, chưa có prefix Redis. */
public final class AuthorizationCacheKeys {
  private AuthorizationCacheKeys() {}

  public static String user(UUID userId) {
    return "auth:user:" + Objects.requireNonNull(userId, "userId");
  }

  public static String session(UUID sessionId) {
    return "auth:session:" + Objects.requireNonNull(sessionId, "sessionId");
  }

  /** Membership authority is scoped to one user in one tenant. */
  public static String member(UUID tenantId, UUID userId) {
    return "auth:member:"
        + Objects.requireNonNull(tenantId, "tenantId")
        + ":"
        + Objects.requireNonNull(userId, "userId");
  }

  public static String tenant(UUID tenantId) {
    return "auth:tenant:" + Objects.requireNonNull(tenantId, "tenantId");
  }

  /** Cùng tên role ở hai tenant vẫn có thể có quyền khác nhau. */
  public static String role(UUID tenantId, UUID roleId) {
    return "auth:role:"
        + Objects.requireNonNull(tenantId, "tenantId")
        + ":"
        + Objects.requireNonNull(roleId, "roleId");
  }
}
