package com.vishop.iam.modules.identity.infrastructure.persistence.repositories;

import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.modules.identity.domain.entity.Session;
import com.vishop.iam.modules.identity.domain.entity.User;
import com.vishop.iam.modules.identity.domain.valueobject.SystemRole;
import org.springframework.jdbc.core.RowMapper;

public final class IdentityJdbcMappings {
  private IdentityJdbcMappings() {}

  public static final RowMapper<User> USER =
      (r, n) ->
          new User(
              uuid(r, "id"),
              r.getString("email"),
              r.getString("password_hash"),
              r.getString("full_name"),
              SystemRole.valueOf(r.getString("system_role")),
              r.getString("status"),
              r.getTimestamp("created_at").toInstant());

  public static final RowMapper<Session> SESSION =
      (r, n) ->
          new Session(
              uuid(r, "id"),
              uuid(r, "user_id"),
              uuid(r, "active_tenant_id"),
              r.getTimestamp("expires_at").toInstant());
}
