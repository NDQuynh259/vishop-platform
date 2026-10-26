package com.vishop.iam.modules.tenant.application.api;

import com.vishop.iam.modules.tenant.application.dto.TenantResponseDto;
import java.util.UUID;

/** Public in-process tenant contract; callers share the same JVM and transaction manager. */
public interface TenantApi {
  /** Creates the tenant and its outbox event inside the caller's existing transaction. */
  TenantResponseDto create(UUID ownerId, String code, String name);

  /** Locks the tenant until the caller's existing transaction completes. */
  void lockTenant(UUID tenantId);

  boolean isActive(UUID tenantId);

  TenantResponseDto view(UUID tenantId);
}
