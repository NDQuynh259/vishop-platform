package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.application.api.TenantApi.MemberReadPort;
import com.vishop.iam.modules.tenant.domain.entity.Membership;
import com.vishop.iam.modules.tenant.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataMembershipRepository;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataRoleRepository;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepositoryAdapter implements MembershipRepositoryPort {
  private final SpringDataMembershipRepository members;
  private final SpringDataRoleRepository roles;
  private final MemberReadPort policyReads;

  public MembershipRepositoryAdapter(
      SpringDataMembershipRepository members,
      SpringDataRoleRepository roles,
      MemberReadPort policyReads) {
    this.members = members;
    this.roles = roles;
    this.policyReads = policyReads;
  }

  public Optional<Membership> membership(UUID user, UUID tenant) {
    return members
        .findByUserIdAndTenantId(user, tenant)
        .flatMap(
            member ->
                roles
                    .findByTenantIdAndId(tenant, member.getRoleId())
                    .map(role -> member.toDomain(role.toDomain())));
  }

  public void addMembership(UUID user, UUID tenant, UUID role) {
    members.saveAndFlush(MembershipJpaEntity.create(UuidV7.nextId(), tenant, user, role));
  }

  public void updateMembership(UUID user, UUID tenant, UUID role, String status) {
    members
        .findByUserIdAndTenantId(user, tenant)
        .ifPresent(
            entity -> {
              entity.update(role, status);
              members.saveAndFlush(entity);
            });
  }

  public int activeOwners(UUID tenant) {
    return policyReads.activeOwners(tenant);
  }
}
