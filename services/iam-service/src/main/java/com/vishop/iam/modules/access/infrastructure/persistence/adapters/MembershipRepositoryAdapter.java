package com.vishop.iam.modules.access.infrastructure.persistence.adapters;

import static com.vishop.iam.modules.access.infrastructure.persistence.repositories.AccessJdbcMappings.*;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.modules.access.domain.entity.Membership;
import com.vishop.iam.modules.access.domain.port.MembershipRepositoryPort;
import io.github.coreplatform.common.domain.UuidV7;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepositoryAdapter implements MembershipRepositoryPort {
  private final JdbcTemplate db;

  public MembershipRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Optional<Membership> membership(UUID user, UUID tenant) {
    return first(
        db.query(
            """
                SELECT m.*,coalesce(r.builtin_key,r.name) AS role_key,r.require_two_factor
                FROM tenant_members m JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id
                WHERE m.user_id=? AND m.tenant_id=?
                """,
            (r, n) ->
                new Membership(
                    uuid(r, "id"),
                    uuid(r, "tenant_id"),
                    uuid(r, "user_id"),
                    uuid(r, "role_id"),
                    r.getString("role_key"),
                    r.getString("status"),
                    r.getBoolean("require_two_factor"),
                    permissions(uuid(r, "role_id"))),
            user,
            tenant));
  }

  @Override
  public void addMembership(UUID user, UUID tenant, UUID role) {
    db.update(
        "INSERT INTO tenant_members(id,user_id,tenant_id,role_id) VALUES (?,?,?,?)",
        UuidV7.nextId(),
        user,
        tenant,
        role);
  }

  @Override
  public void updateMembership(UUID user, UUID tenant, UUID role, String status) {
    db.update(
        "UPDATE tenant_members SET role_id=?,status=?,updated_at=now(),version=version+1 WHERE user_id=? AND tenant_id=?",
        role,
        status,
        user,
        tenant);
  }

  @Override
  public int activeOwners(UUID tenant) {
    return db.queryForObject(
        """
                SELECT count(*) FROM tenant_members m JOIN roles r ON r.id=m.role_id
                JOIN users u ON u.id=m.user_id WHERE m.tenant_id=? AND m.status='ACTIVE'
                AND r.builtin_key='OWNER' AND u.status='ACTIVE'
                """,
        Integer.class,
        tenant);
  }

  private Set<String> permissions(UUID role) {
    return Set.copyOf(
        db.queryForList(
            "SELECT permission_key FROM role_permissions WHERE role_id=?", String.class, role));
  }
}
