package io.github.coreplatform.common.security.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility helper for retrieving authenticated user details and tenant context from SecurityContextHolder.
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {}

    /**
     * Retrieves the current authenticated CommonUserDetails if present.
     */
    public static Optional<CommonUserDetails> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CommonUserDetails userDetails) {
            return Optional.of(userDetails);
        }
        return Optional.empty();
    }

    /**
     * Retrieves the current authenticated CommonUserDetails or throws IllegalStateException.
     */
    public static CommonUserDetails requiredCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Authenticated user context is required for this operation"));
    }

    /**
     * Retrieves the current authenticated userId if present.
     */
    public static Optional<UUID> getCurrentUserId() {
        return getCurrentUser().map(CommonUserDetails::userId);
    }

    /**
     * Retrieves the current authenticated userId or throws IllegalStateException.
     */
    public static UUID requiredCurrentUserId() {
        return requiredCurrentUser().userId();
    }

    /**
     * Retrieves the current tenantId from user context or TenantContext.
     */
    public static Optional<UUID> getCurrentTenantId() {
        Optional<UUID> userTenantId = getCurrentUser().map(CommonUserDetails::tenantId);
        if (userTenantId.isPresent()) {
            return userTenantId;
        }
        return Optional.ofNullable(TenantContext.get());
    }

    /**
     * Retrieves the current tenantId or throws IllegalStateException.
     */
    public static UUID requiredCurrentTenantId() {
        return getCurrentTenantId()
                .orElseThrow(() -> new IllegalStateException("Tenant context is required for this operation"));
    }
}
