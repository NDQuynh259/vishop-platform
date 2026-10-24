package com.vishop.iam.contracts.tenant;

import java.util.UUID;

public interface TenantApi {
  /** Creates the tenant and its outbox event inside the caller's existing transaction. */
  TenantResponseDto create(UUID ownerId, String code, String name);

  /** Locks the tenant until the caller's existing transaction completes. */
  void lockTenant(UUID tenantId);

  boolean isActive(UUID tenantId);

  TenantResponseDto view(UUID tenantId);
}
