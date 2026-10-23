package com.vishop.iam.identity.infrastructure.persistence.adapters;

import static com.vishop.iam.identity.infrastructure.persistence.repositories.IdentityJdbcMappings.USER;
import static com.vishop.iam.shared.infrastructure.persistence.JdbcValues.*;

import com.vishop.iam.identity.application.dto.UserView;
import com.vishop.iam.identity.application.port.UserReadPort;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserReadAdapter implements UserReadPort {
  private final JdbcTemplate db;

  public UserReadAdapter(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public List<UserView> users(int limit, int offset) {
    return db
        .query(
            "SELECT * FROM users ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            USER,
            limit,
            offset)
        .stream()
        .map(UserView::from)
        .toList();
  }
}
