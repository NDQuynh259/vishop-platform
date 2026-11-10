package io.github.coreplatform.common.security.context;

import io.github.coreplatform.common.security.gateway.GatewayPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility helper for retrieving authenticated user details and tenant context from
 * SecurityContextHolder.
 */
public final class SecurityContextHelper {

  // #region SecurityContextHelper
  private SecurityContextHelper() {}

  // #endregion

  // #region getCurrentUser
  /** Retrieves the current authenticated CommonUserDetails if present. */
  public static Optional<CommonUserDetails> getCurrentUser() {
    return authenticatedPrincipal(CommonUserDetails.class);
  }

  // #endregion

  // #region requiredCurrentUser
  /** Retrieves the current authenticated CommonUserDetails or throws IllegalStateException. */
  public static CommonUserDetails requiredCurrentUser() {
    return getCurrentUser()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Authenticated user context is required for this operation"));
  }

  // #endregion

  // #region getCurrentUserId
  /** Retrieves the current authenticated userId if present. */
  public static Optional<UUID> getCurrentUserId() {
    return getCurrentUser()
        .map(CommonUserDetails::userId)
        .or(() -> authenticatedPrincipal(GatewayPrincipal.class).map(GatewayPrincipal::userId));
  }

  // #endregion

  // #region requiredCurrentUserId
  /** Retrieves the current authenticated userId or throws IllegalStateException. */
  public static UUID requiredCurrentUserId() {
    return getCurrentUserId()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Authenticated user context is required for this operation"));
  }

  // #endregion

  // #region getCurrentTenantId
  /** Retrieves the current tenantId from user context or TenantContext. */
  public static Optional<UUID> getCurrentTenantId() {
    return getCurrentUser()
        .map(CommonUserDetails::tenantId)
        .or(() -> authenticatedPrincipal(GatewayPrincipal.class).map(GatewayPrincipal::tenantId))
        .or(() -> Optional.ofNullable(TenantContext.get()));
  }

  private static <T> Optional<T> authenticatedPrincipal(Class<T> type) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && type.isInstance(auth.getPrincipal())) {
      return Optional.of(type.cast(auth.getPrincipal()));
    }
    return Optional.empty();
  }

  // #endregion

  // #region requiredCurrentTenantId
  /** Retrieves the current tenantId or throws IllegalStateException. */
  public static UUID requiredCurrentTenantId() {
    return getCurrentTenantId()
        .orElseThrow(
            () -> new IllegalStateException("Tenant context is required for this operation"));
  }
  // #endregion
}
