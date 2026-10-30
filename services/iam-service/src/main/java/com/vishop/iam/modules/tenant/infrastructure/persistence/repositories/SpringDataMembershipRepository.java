package com.vishop.iam.modules.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {
  Optional<MembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);

  @Query(
      """
      select m from MembershipJpaEntity m, TenantJpaEntity t, RoleJpaEntity r
      where m.userId=:user and m.tenantId=:tenant and m.status='ACTIVE' and m.deletedAt is null
        and t.id=m.tenantId and t.status=com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus.ACTIVE and t.deletedAt is null
        and r.id=m.roleId and r.tenantId=m.tenantId and r.deletedAt is null and r.requireTwoFactor=false
      """)
  Optional<MembershipJpaEntity> authenticationMembership(
      @org.springframework.data.repository.query.Param("user") UUID user,
      @org.springframework.data.repository.query.Param("tenant") UUID tenant);

  @Query(
      """
      select t.id from TenantJpaEntity t where t.status=com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus.ACTIVE
        and t.deletedAt is null and exists (select m.id from MembershipJpaEntity m, RoleJpaEntity r
          where m.tenantId=t.id and m.userId=:user and m.status='ACTIVE' and m.deletedAt is null
          and r.id=m.roleId and r.tenantId=m.tenantId and r.deletedAt is null and r.requireTwoFactor=false)
      order by t.name,t.id
      """)
  List<UUID> activeTenantIds(@org.springframework.data.repository.query.Param("user") UUID user);
}
