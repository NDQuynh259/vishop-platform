package com.vishop.iam.access.infrastructure.persistence.repositories;

import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.access.domain.entities.Invitation;
import org.springframework.jdbc.core.RowMapper;

public final class AccessJdbcMappings {
  private AccessJdbcMappings() {}

  public static final RowMapper<Invitation> INVITATION =
      (r, n) ->
          new Invitation(
              uuid(r, "id"),
              uuid(r, "tenant_id"),
              r.getString("email"),
              uuid(r, "role_id"),
              uuid(r, "invited_by"),
              r.getString("status"),
              r.getTimestamp("expires_at").toInstant());
}
