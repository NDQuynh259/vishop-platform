package com.vishop.iam.modules.tenant.application.api;

import com.vishop.iam.modules.tenant.application.dto.MemberView;
import com.vishop.iam.modules.tenant.application.dto.TenantMemberResponseDto;
import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import com.vishop.iam.shared.application.dto.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public tenant boundary: tenant lifecycle, memberships and tenant-scoped grants. */
public interface TenantApi {
  enum Status {
    ACTIVE,
    SUSPENDED,
    ARCHIVED
  }

  record Grant(UUID roleId, String roleKey, Set<String> permissions) {
    public Grant {
      permissions = Set.copyOf(permissions);
    }
  }

  /** User/membership projections implemented by composition; tenant owns all writes. */
  interface MemberReadPort {
    Optional<TenantMemberResponseDto> member(UUID tenantId, UUID userId);

    List<MemberView> members(UUID tenantId);

    int activeOwners(UUID tenantId);
  }

  void requireAccess(AuthenticatedUser actor, UUID tenantId, String permission);

  List<UUID> activeTenantIds(UUID userId);

  Optional<Grant> activeGrant(UUID userId, UUID tenantId);

  boolean hasActiveRole(UUID userId, UUID tenantId, UUID roleId, String roleKey);

  /** Mutation APIs participate in the caller's transaction. */
  TenantResponseDto create(UUID ownerId, String code, String name);

  void initializeOwner(UUID userId, UUID tenantId);

  void lockTenant(UUID tenantId);

  boolean isActive(UUID tenantId);

  TenantResponseDto view(UUID tenantId);

  List<TenantResponseDto> tenants(int limit, int offset);

  TenantResponseDto rename(UUID tenantId, String name, UUID actorId);

  TenantResponseDto update(UUID tenantId, String name, Status status, UUID actorId);
}
