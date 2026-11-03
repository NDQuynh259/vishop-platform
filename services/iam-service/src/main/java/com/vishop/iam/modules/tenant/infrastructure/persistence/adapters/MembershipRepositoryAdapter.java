package com.vishop.iam.modules.tenant.infrastructure.persistence.adapters;

import com.vishop.iam.modules.tenant.application.api.TenantApi.MemberReadPort;
import com.vishop.iam.modules.tenant.domain.entity.Membership;
import com.vishop.iam.modules.tenant.domain.port.MembershipRepositoryPort;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataMembershipRepository;
import com.vishop.iam.modules.tenant.infrastructure.persistence.repositories.SpringDataRoleRepository;
import com.vishop.iam.shared.application.port.AuthorizationDeliveryPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.constants.MembershipStatus;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class MembershipRepositoryAdapter implements MembershipRepositoryPort {
  private final SpringDataMembershipRepository members;
  private final SpringDataRoleRepository roles;
  private final MemberReadPort policyReads;
  private final AuthorizationDeliveryPort delivery;

  public MembershipRepositoryAdapter(
      SpringDataMembershipRepository members,
      SpringDataRoleRepository roles,
      MemberReadPort policyReads,
      AuthorizationDeliveryPort delivery) {
    this.members = members;
    this.roles = roles;
    this.policyReads = policyReads;
    this.delivery = delivery;
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

  public Optional<Membership> authenticationMembership(UUID user, UUID tenant) {
    return members
        .authenticationMembership(user, tenant)
        .flatMap(
            m ->
                roles
                    .findByTenantIdAndId(tenant, m.getRoleId())
                    .map(r -> m.toDomain(r.toDomain())));
  }

  public List<UUID> activeTenantIds(UUID user) {
    return members.activeTenantIds(user);
  }

  public void addMembership(UUID user, UUID tenant, UUID role) {
    members.saveAndFlush(MembershipJpaEntity.create(UuidV7.nextId(), tenant, user, role));
    deliverAfterCommit(tenant, user);
  }

  public void updateMembership(UUID user, UUID tenant, UUID role, MembershipStatus status) {
    members
        .findByUserIdAndTenantId(user, tenant)
        .ifPresent(
            entity -> {
              entity.update(role, status);
              members.saveAndFlush(entity);
              deliverAfterCommit(tenant, user);
            });
  }

  public int activeOwners(UUID tenant) {
    return policyReads.activeOwners(tenant);
  }

  private void deliverAfterCommit(UUID tenant, UUID user) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Membership changes require a transaction");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            delivery.deliverMembership(tenant, user);
          }
        });
  }
}
