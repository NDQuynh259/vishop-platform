package com.vishop.iam.composition.members.infrastructure.persistence;

import com.vishop.iam.composition.members.application.port.TenantMemberReadPort;
import com.vishop.iam.modules.access.application.dto.TenantMemberResponseDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** JPA native read projection within IAM; writes remain owned by each business module. */
@Repository
public class JpaTenantMemberReadAdapter implements TenantMemberReadPort {
  private final EntityManager entityManager;

  public JpaTenantMemberReadAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Optional<TenantMemberResponseDto> member(UUID tenant, UUID user) {
    List<?> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id,t.id AS tenant_id,u.id AS user_id,u.email,u.full_name,r.id AS role_id,
                       coalesce(r.builtin_key,r.name) AS role,m.status,
                       coalesce(array_agg(p.permission_key ORDER BY p.permission_key)
                           FILTER (WHERE p.permission_key IS NOT NULL),
                           CAST(ARRAY[] AS varchar[])) AS permissions
                FROM tenants t
                JOIN tenant_members m ON m.tenant_id=t.id
                JOIN users u ON u.id=m.user_id
                JOIN roles r ON r.id=m.role_id AND r.tenant_id=t.id
                LEFT JOIN role_permissions p ON p.role_id=r.id
                WHERE t.id=:tenantId AND u.id=:userId
                GROUP BY m.id,t.id,u.id,r.id
                """,
                Tuple.class)
            .setParameter("tenantId", tenant)
            .setParameter("userId", user)
            .getResultList();

    if (rows.isEmpty()) return Optional.empty();
    Tuple row = (Tuple) rows.getFirst();
    return Optional.of(
        new TenantMemberResponseDto(
            row.get("id", UUID.class),
            row.get("tenant_id", UUID.class),
            row.get("user_id", UUID.class),
            row.get("email", String.class),
            row.get("full_name", String.class),
            row.get("role_id", UUID.class),
            row.get("role", String.class),
            row.get("status", String.class),
            Set.copyOf(Arrays.asList(row.get("permissions", String[].class)))));
  }
}
