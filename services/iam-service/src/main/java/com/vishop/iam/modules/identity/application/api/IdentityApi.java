package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.UUID;

/** Public in-process identity contract; implementation and persistence stay inside this module. */
public interface IdentityApi {
  interface AuthorizationDeliveryPort {
    void deliverSession(UUID sessionId);

    class Unavailable extends RuntimeException {
      public Unavailable(Throwable cause) {
        super("Authorization cache is unavailable", cause);
      }
    }
  }

  record TenantGrant(UUID roleId, String roleKey, java.util.Set<String> permissions) {
    public TenantGrant {
      permissions = java.util.Set.copyOf(permissions);
    }
  }

  interface TenantAccessPort {
    java.util.List<UUID> activeTenantIds(UUID userId);

    java.util.Optional<TenantGrant> activeGrant(UUID userId, UUID tenantId);
  }

  AuthenticatedUser resolveContext(AuthenticatedUser context, UUID roleId);

  AuthenticatedUser verifyAccessToken(String token);

  UserView requireActive(UUID userId);

  void requireCms(AuthenticatedUser actor);

  boolean isActive(UUID userId);
}
