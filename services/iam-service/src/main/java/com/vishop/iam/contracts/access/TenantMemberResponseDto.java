package com.vishop.iam.contracts.access;

import java.util.Set;
import java.util.UUID;

/**
 * Tenant membership and its assigned role permissions; status controls whether access is active.
 */
public record TenantMemberResponseDto(
    UUID id,
    UUID tenantId,
    UUID userId,
    String email,
    String fullName,
    UUID roleId,
    String role,
    String status,
    Set<String> permissions) {
  public TenantMemberResponseDto {
    permissions = Set.copyOf(permissions);
  }
}
