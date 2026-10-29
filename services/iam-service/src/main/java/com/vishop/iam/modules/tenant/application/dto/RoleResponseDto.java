package com.vishop.iam.modules.tenant.application.dto;

import com.vishop.iam.modules.tenant.domain.entity.Role;
import java.util.Set;
import java.util.UUID;

public record RoleResponseDto(
    UUID id,
    UUID tenantId,
    String name,
    String builtinKey,
    boolean requireTwoFactor,
    Set<String> permissions) {
  public RoleResponseDto {
    permissions = Set.copyOf(permissions);
  }

  public static RoleResponseDto from(Role role) {
    return new RoleResponseDto(
        role.id(),
        role.tenantId(),
        role.name(),
        role.builtinKey(),
        role.requireTwoFactor(),
        role.permissions());
  }
}
