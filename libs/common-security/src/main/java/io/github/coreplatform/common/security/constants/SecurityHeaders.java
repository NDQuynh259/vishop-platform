package io.github.coreplatform.common.security.constants;

/**
 * Standard HTTP header constants used for security context propagation
 * between API Gateway and downstream microservices.
 */
public final class SecurityHeaders {

    // #region SecurityHeaders
    private SecurityHeaders() {}
    // #endregion

    public static final String X_USER_ID = "X-User-Id";
    public static final String X_TENANT_ID = "X-Tenant-Id";
    public static final String X_USER_ROLES = "X-User-Roles";
    public static final String X_USER_PERMISSIONS = "X-User-Permissions";
    public static final String X_USER_SHOPS = "X-User-Shops";
}
