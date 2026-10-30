package com.vishop.iam.modules.identity.application.api;

import com.vishop.iam.modules.identity.application.dto.IamTokenClaims;
import com.vishop.iam.modules.identity.application.dto.UserView;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public in-process identity contract; implementation and persistence stay inside this module. */
public interface IdentityApi {
  /** Identity-owned outbound contract; composition supplies access integration. */
  interface TenantAccessPort {
    List<UUID> activeTenantIds(UUID userId);

    Optional<TenantGrant> activeGrant(UUID userId, UUID tenantId);

    boolean hasActiveRole(UUID userId, UUID tenantId, UUID roleId, String roleKey);
  }

  record TenantGrant(UUID roleId, String roleKey, Set<String> permissions) {
    public TenantGrant {
      permissions = Set.copyOf(permissions);
    }
  }

  IamTokenClaims.AccessToken verifyAccessToken(String accessToken);

  UserView requireActive(UUID userId);

  void requireCms(AuthenticatedUser actor);

  boolean isActive(UUID userId);
}
