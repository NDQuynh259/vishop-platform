package io.github.coreplatform.common.security.context;

import java.util.UUID;

/**
 * Request-scoped tenant context holding the current tenantId in ThreadLocal.
 * Must be set by trusted gateway/security headers and cleared after execution.
 */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID requiredTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required for this operation");
        }
        return tenantId;
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
