package com.vishop.iam.modules.access.infrastructure.persistence.adapters;

import com.vishop.iam.modules.access.application.dto.MemberView;
import com.vishop.iam.modules.access.application.dto.MyTenant;
import com.vishop.iam.modules.access.application.port.AccessReadPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class AccessReadAdapter implements AccessReadPort {
  private final EntityManager entityManager;

  public AccessReadAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public List<MyTenant> myTenants(UUID user) {
    List<?> rows =
        entityManager
            .createNativeQuery(
                """
    SELECT t.id,t.code,t.name,m.role_id,coalesce(r.builtin_key,r.name) AS role,t.status
    FROM tenant_members m JOIN tenants t ON t.id=m.tenant_id
    JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id
    WHERE m.user_id=:user AND m.status='ACTIVE' AND t.status='ACTIVE' ORDER BY t.name,t.id
    """,
                Tuple.class)
            .setParameter("user", user)
            .getResultList();
    return rows.stream()
        .map(
            value -> {
              var r = (Tuple) value;
              return new MyTenant(
                  r.get("id", UUID.class),
                  r.get("code", String.class),
                  r.get("name", String.class),
                  r.get("role_id", UUID.class),
                  r.get("role", String.class),
                  r.get("status", String.class));
            })
        .toList();
  }

  public List<MemberView> members(UUID tenant) {
    List<?> rows =
        entityManager
            .createNativeQuery(
                """
    SELECT m.id,m.user_id,u.email,u.full_name,m.role_id,coalesce(r.builtin_key,r.name) AS role,m.status
    FROM tenant_members m JOIN users u ON u.id=m.user_id
    JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id
    WHERE m.tenant_id=:tenant ORDER BY u.email
    """,
                Tuple.class)
            .setParameter("tenant", tenant)
            .getResultList();
    return rows.stream()
        .map(
            value -> {
              var r = (Tuple) value;
              return new MemberView(
                  r.get("id", UUID.class),
                  r.get("user_id", UUID.class),
                  r.get("email", String.class),
                  r.get("full_name", String.class),
                  r.get("role_id", UUID.class),
                  r.get("role", String.class),
                  r.get("status", String.class));
            })
        .toList();
  }
}
