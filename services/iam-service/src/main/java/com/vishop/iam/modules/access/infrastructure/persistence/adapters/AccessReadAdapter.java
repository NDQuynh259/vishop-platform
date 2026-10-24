package com.vishop.iam.modules.access.infrastructure.persistence.adapters;

import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.modules.access.application.dto.MemberView;
import com.vishop.iam.modules.access.application.dto.MyTenant;
import com.vishop.iam.modules.access.application.port.AccessReadPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccessReadAdapter implements AccessReadPort {
  private final JdbcTemplate db;

  public AccessReadAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public List<MyTenant> myTenants(UUID user) {
    return db.query(
        """
                SELECT t.id,t.code,t.name,m.role_id,coalesce(r.builtin_key,r.name) AS role,t.status
                FROM tenant_members m JOIN tenants t ON t.id=m.tenant_id
                JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id
                WHERE m.user_id=? AND m.status='ACTIVE' AND t.status='ACTIVE' ORDER BY t.name,t.id
                """,
        (r, n) ->
            new MyTenant(
                uuid(r, "id"),
                r.getString("code"),
                r.getString("name"),
                uuid(r, "role_id"),
                r.getString("role"),
                r.getString("status")),
        user);
  }

  @Override
  public List<MemberView> members(UUID tenant) {
    return db.query(
        """
                SELECT m.id,m.user_id,u.email,u.full_name,m.role_id,coalesce(r.builtin_key,r.name) AS role,m.status
                FROM tenant_members m JOIN users u ON u.id=m.user_id
                JOIN roles r ON r.id=m.role_id AND r.tenant_id=m.tenant_id WHERE m.tenant_id=? ORDER BY u.email
                """,
        (r, n) ->
            new MemberView(
                uuid(r, "id"),
                uuid(r, "user_id"),
                r.getString("email"),
                r.getString("full_name"),
                uuid(r, "role_id"),
                r.getString("role"),
                r.getString("status")),
        tenant);
  }
}
