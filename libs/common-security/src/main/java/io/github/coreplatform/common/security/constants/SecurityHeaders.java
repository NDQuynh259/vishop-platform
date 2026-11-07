package io.github.coreplatform.common.security.constants;

import java.util.Locale;
import java.util.Set;

/**
 * Standard HTTP header constants used for security context propagation between API Gateway and
 * downstream microservices.
 */
public final class SecurityHeaders {

  // #region SecurityHeaders
  private SecurityHeaders() {}

  // #endregion

  public static final String X_USER_ID = "X-User-Id";
  public static final String X_SESSION_ID = "X-Session-Id";
  public static final String X_TENANT_ID = "X-Tenant-Id";
  public static final String X_ROLE_ID = "X-Role-Id";
  public static final String X_SYSTEM_ROLE = "X-System-Role";
  // Legacy metadata/grant headers: strip client input, never forward these to a backend.
  public static final String X_TENANT_ROLE = "X-Tenant-Role";
  public static final String X_PERMISSIONS = "X-Permissions";
  public static final String X_USER_ROLES = "X-User-Roles";
  public static final String X_USER_PERMISSIONS = "X-User-Permissions";
  public static final String X_USER_SHOPS = "X-User-Shops";
  public static final String X_GATEWAY_CONTEXT = "X-Gateway-Context";
  public static final String X_IAM_INTERNAL_KEY = "X-Iam-Internal-Key";

  /** Header nội bộ phải loại bỏ khỏi request client trước khi gateway tạo context tin cậy. */
  public static final Set<String> INTERNAL_HEADERS =
      Set.of(
          X_USER_ID,
          X_SESSION_ID,
          X_SYSTEM_ROLE,
          X_ROLE_ID,
          X_TENANT_ROLE,
          X_PERMISSIONS,
          X_TENANT_ID,
          X_USER_ROLES,
          X_USER_PERMISSIONS,
          X_USER_SHOPS,
          X_GATEWAY_CONTEXT,
          X_IAM_INTERNAL_KEY);

  private static final Set<String> INTERNAL_HEADER_PREFIXES =
      Set.of("x-user-", "x-gateway-", "x-tenant-", "x-session-", "x-system-", "x-role-");

  /** Không phân biệt hoa/thường; chặn cả header tự đặt thêm thuộc prefix nội bộ. */
  public static boolean isInternalHeader(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    return INTERNAL_HEADERS.stream().anyMatch(name::equalsIgnoreCase)
        || INTERNAL_HEADER_PREFIXES.stream().anyMatch(normalized::startsWith);
  }
}
