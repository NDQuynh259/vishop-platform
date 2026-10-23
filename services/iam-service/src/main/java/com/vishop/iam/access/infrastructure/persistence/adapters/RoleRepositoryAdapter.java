package com.vishop.iam.access.infrastructure.persistence.adapters;

import static com.vishop.iam.access.infrastructure.persistence.repositories.AccessJdbcMappings.*;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.access.domain.entities.Role;
import com.vishop.iam.access.domain.ports.RoleRepositoryPort;
import io.github.coreplatform.common.domain.UuidV7;
import io.github.coreplatform.common.domain.exception.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryAdapter implements RoleRepositoryPort {
  private void replacePermissions(UUID role, Set<String> permissions) {
    db.update("DELETE FROM role_permissions WHERE role_id=?", role);
    for (String key : permissions)
      db.update("INSERT INTO role_permissions(role_id,permission_key) VALUES (?,?)", role, key);
  }

  private final JdbcTemplate db;

  public RoleRepositoryAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public Role role(UUID tenant, UUID role) {
    return first(
            db.query(
                "SELECT * FROM roles WHERE tenant_id=? AND id=?",
                (r, n) ->
                    new Role(
                        uuid(r, "id"),
                        uuid(r, "tenant_id"),
                        r.getString("name"),
                        r.getString("builtin_key"),
                        r.getBoolean("require_two_factor"),
                        permissions(uuid(r, "id"))),
                tenant,
                role))
        .orElseThrow(() -> new EntityNotFoundException("Role", role));
  }

  @Override
  public UUID ownerRole(UUID tenant) {
    return db.queryForObject(
        "SELECT id FROM roles WHERE tenant_id=? AND builtin_key='OWNER'", UUID.class, tenant);
  }

  @Override
  public List<Role> roles(UUID tenant) {
    return db
        .queryForList("SELECT id FROM roles WHERE tenant_id=? ORDER BY name", UUID.class, tenant)
        .stream()
        .map(id -> role(tenant, id))
        .toList();
  }

  @Override
  public UUID createRole(UUID tenant, String name, Set<String> permissions) {
    UUID id = UuidV7.nextId();
    db.update("INSERT INTO roles(id,tenant_id,name) VALUES (?,?,?)", id, tenant, name);
    replacePermissions(id, permissions);
    return id;
  }

  @Override
  public void updateRole(UUID tenant, UUID role, String name, Set<String> permissions) {
    db.update(
        "UPDATE roles SET name=?,updated_at=now(),version=version+1 WHERE tenant_id=? AND id=?",
        name,
        tenant,
        role);
    replacePermissions(role, permissions);
  }

  private Set<String> permissions(UUID role) {
    return Set.copyOf(
        db.queryForList(
            "SELECT permission_key FROM role_permissions WHERE role_id=?", String.class, role));
  }
}
