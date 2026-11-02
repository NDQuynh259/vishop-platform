package io.github.coreplatform.common.security.gateway;

import java.util.Objects;
import java.util.UUID;

/** Key logic phân quyền, cùng định dạng với authorization_outbox của IAM, chưa có prefix Redis. */
public final class AuthorizationCacheKeys {
  private AuthorizationCacheKeys() {}

  public static String user(UUID userId) {
    return "auth:user:" + Objects.requireNonNull(userId, "userId");
  }

  public static String session(UUID sessionId) {
    return "auth:session:" + Objects.requireNonNull(sessionId, "sessionId");
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
