package com.vishop.iam.modules.access.domain.service;

import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.entity.Role;
import com.vishop.iam.modules.access.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.access.domain.port.RoleRepositoryPort;
import com.vishop.iam.modules.access.domain.port.UserDirectoryPort;
import io.github.coreplatform.common.domain.exception.BadRequestException;
import io.github.coreplatform.common.domain.exception.ConflictException;
import io.github.coreplatform.common.domain.exception.ForbiddenException;
import java.util.Set;
import java.util.UUID;

public final class RoleAssignmentPolicy {
  private final Set<String> catalogue;
  private final UserDirectoryPort userDirectory;
  private final MembershipRepositoryPort membershipRepository;
  private final RoleRepositoryPort roleRepository;

  public RoleAssignmentPolicy(
      Set<String> catalogue,
      UserDirectoryPort userDirectory,
      MembershipRepositoryPort membershipRepository,
      RoleRepositoryPort roleRepository) {
    this.catalogue = Set.copyOf(catalogue);
    this.userDirectory = userDirectory;
    this.membershipRepository = membershipRepository;
    this.roleRepository = roleRepository;
  }

  public void validateGrants(Membership actor, Set<String> permissions) {
    if (!catalogue.containsAll(permissions))
      throw new BadRequestException("Unknown tenant permission");
    if (!actor.permissions().containsAll(permissions))
      throw new ForbiddenException("Cannot grant permissions you do not hold");
  }

  public void canAssign(Membership actor, Role role) {
    validateGrants(actor, role.permissions());
    if ("OWNER".equals(role.builtinKey())
        && !"OWNER".equals(roleRepository.role(actor.tenantId(), actor.roleId()).builtinKey()))
      throw new ForbiddenException("Only an owner can assign the OWNER role");
  }

  public void protectOwner(UUID tenant, Membership member, String newRole, String newStatus) {
    if ("ACTIVE".equals(member.status())
        && "OWNER".equals(roleRepository.role(tenant, member.roleId()).builtinKey())
        && userDirectory.isActive(member.userId())
        && (!"OWNER".equals(newRole) || !"ACTIVE".equals(newStatus))
        && membershipRepository.activeOwners(tenant) <= 1)
      throw new ConflictException("The final active owner cannot be removed or demoted");
  }

  public void protectOwnerManagement(Membership actor, Membership target) {
    if ("OWNER".equals(roleRepository.role(target.tenantId(), target.roleId()).builtinKey())
        && !"OWNER".equals(roleRepository.role(actor.tenantId(), actor.roleId()).builtinKey()))
      throw new ForbiddenException("Only an owner can manage another owner");
  }
}
