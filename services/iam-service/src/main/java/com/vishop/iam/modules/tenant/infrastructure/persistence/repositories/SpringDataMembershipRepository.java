package com.vishop.iam.modules.tenant.infrastructure.persistence.repositories;

import com.vishop.iam.modules.tenant.domain.valueobject.TenantStatus;
import com.vishop.iam.modules.tenant.infrastructure.persistence.entities.MembershipJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {
  Optional<MembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);

  @Query(
      """
      select count(m)>0
      from MembershipJpaEntity m, TenantJpaEntity t, RoleJpaEntity r
      where m.userId=:userId and m.tenantId=:tenantId and m.roleId=:roleId
        and m.status='ACTIVE' and m.deletedAt is null
        and t.id=m.tenantId and t.status=:tenantStatus and t.deletedAt is null
        and r.id=m.roleId and r.tenantId=m.tenantId and r.deletedAt is null
        and r.requireTwoFactor=false
        and (r.builtinKey=:roleKey or
             (r.builtinKey is null and concat('CUSTOM:',cast(r.id as string))=:roleKey))
      """)
  boolean hasActiveRole(
      @Param("userId") UUID userId,
      @Param("tenantId") UUID tenantId,
      @Param("roleId") UUID roleId,
      @Param("roleKey") String roleKey,
      @Param("tenantStatus") TenantStatus tenantStatus);
}
